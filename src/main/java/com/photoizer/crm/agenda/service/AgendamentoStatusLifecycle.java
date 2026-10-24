package com.photoizer.crm.agenda.service;

import com.photoizer.crm.agenda.event.AgendamentoCanceladoEvent;
import com.photoizer.crm.agenda.event.AgendamentoConfirmadoEvent;
import com.photoizer.crm.agenda.event.AgendamentoRealizadoEvent;
import com.photoizer.crm.agenda.event.PagamentoFinalRegistradoEvent;
import com.photoizer.crm.agenda.exception.AgendamentoNaoEncontradoException;
import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoFotografoRepository;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.shared.model.FormaPagamento;
import com.photoizer.crm.shared.storage.FileStorageService;
import com.photoizer.crm.shared.storage.FileValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AgendamentoStatusLifecycle {

    private static final Logger log = LoggerFactory.getLogger(AgendamentoStatusLifecycle.class);

    private final AgendamentoRepository agendamentoRepository;
    private final AgendamentoFotografoRepository agendamentoFotografoRepository;
    private final DisponibilidadeService disponibilidadeService;
    private final ApplicationEventPublisher eventPublisher;
    private final FileStorageService fileStorageService;
    private final FileValidator fileValidator;

    public AgendamentoStatusLifecycle(AgendamentoRepository agendamentoRepository,
                                      AgendamentoFotografoRepository agendamentoFotografoRepository,
                                      DisponibilidadeService disponibilidadeService,
                                      ApplicationEventPublisher eventPublisher,
                                      FileStorageService fileStorageService,
                                      FileValidator fileValidator) {
        this.agendamentoRepository = agendamentoRepository;
        this.agendamentoFotografoRepository = agendamentoFotografoRepository;
        this.disponibilidadeService = disponibilidadeService;
        this.eventPublisher = eventPublisher;
        this.fileStorageService = fileStorageService;
        this.fileValidator = fileValidator;
    }

    public Agendamento atualizarStatus(UUID id, String novoStatus) {
        var agendamento = buscarPorId(id);
        var statusAnterior = agendamento.getStatus();
        var status = StatusAgendamento.valueOf(novoStatus);
        agendamento.transicionarPara(status);

        // Consistência de eventos: publicar SOMENTE após a persistência bem-sucedida,
        // para que listeners (financeiro, comissão, documento) não reajam a um estado
        // que não foi salvo (ex.: rollback/falha de save).
        agendamento = agendamentoRepository.save(agendamento);

        if (status == StatusAgendamento.REALIZADO) {
            var fotografoIds = agendamentoFotografoRepository.findByAgendamentoId(agendamento.getId())
                .stream().map(af -> af.getFotografo().getId()).toList();
            var clienteNome = agendamento.nomeCliente();
            eventPublisher.publishEvent(new AgendamentoRealizadoEvent(
                agendamento.getId(),
                agendamento.getCliente() != null ? agendamento.getCliente().getId() : null,
                clienteNome,
                fotografoIds
            ));
        }

        if (status == StatusAgendamento.CANCELADO || status == StatusAgendamento.NO_SHOW) {
            eventPublisher.publishEvent(new AgendamentoCanceladoEvent(agendamento.getId()));
        }

        log.atInfo()
            .addKeyValue("event_action", "agendamento.status_alterado")
            .addKeyValue("agendamento_id", agendamento.getId())
            .addKeyValue("status_anterior", statusAnterior)
            .addKeyValue("status_novo", status)
            .log("Status do agendamento alterado");

        return agendamento;
    }

    public Agendamento reagendar(UUID id, LocalDate data, String hora, Integer duracaoMinutos) {
        var agendamento = buscarPorId(id);

        LocalTime time = (hora != null && !hora.isBlank())
            ? LocalTime.parse(hora, DateTimeFormatter.ofPattern("HH:mm"))
            : agendamento.getDataHoraEnsaio().toLocalTime();

        LocalDate novaData = (data != null) ? data : agendamento.getDataHoraEnsaio().toLocalDate();
        LocalDateTime novaDataHora = LocalDateTime.of(novaData, time);

        int duracao = (duracaoMinutos != null) ? duracaoMinutos : agendamento.getDuracaoMinutos();

        var pacote = agendamento.getPacote();
        var fotografoId = agendamento.getFotografo() != null ? agendamento.getFotografo().getId() : null;
        disponibilidadeService.validarConflitoAgenda(
            pacote, novaDataHora, duracao, agendamento.getLocalEnsaio(),
            ConflitoAgendaParams.paraAtualizacao(fotografoId, agendamento.getId()));

        agendamento.reagendar(novaDataHora, duracao);

        agendamento = agendamentoRepository.save(agendamento);

        eventPublisher.publishEvent(new AgendamentoConfirmadoEvent(
            agendamento.getId(),
            agendamento.getCliente() != null ? agendamento.getCliente().getId() : null
        ));

        log.atInfo()
            .addKeyValue("event_action", "agendamento.reagendado")
            .addKeyValue("agendamento_id", agendamento.getId())
            .addKeyValue("data_hora_ensaio", agendamento.getDataHoraEnsaio().toString())
            .addKeyValue("duracao_minutos", duracao)
            .log("Agendamento reagendado");

        return agendamento;
    }

    public Agendamento toggleDestaque(UUID id) {
        var agendamento = buscarPorId(id);
        agendamento.alternarDestaque();
        return agendamentoRepository.save(agendamento);
    }

    public Agendamento registrarPagamentoFinal(UUID id, MultipartFile comprovante, FormaPagamento formaPagamento) {
        var agendamento = buscarPorId(id);

        // O comprovante é opcional: o registro do pagamento final não depende de anexo.
        var temComprovante = comprovante != null && !comprovante.isEmpty();

        String url = null;
        if (temComprovante) {
            fileValidator.validate(comprovante, "any");
            url = fileStorageService.salvar(comprovante);
        }

        agendamento.aplicarPagamentoFinal(url, formaPagamento);

        agendamento = agendamentoRepository.save(agendamento);

        var fotografoIds = agendamentoFotografoRepository.findByAgendamentoId(agendamento.getId())
            .stream().map(af -> af.getFotografo().getId()).toList();
        var clienteNome = agendamento.getCliente() != null
            ? agendamento.nomeCliente() : "";
        eventPublisher.publishEvent(new PagamentoFinalRegistradoEvent(
            agendamento.getId(),
            agendamento.getValorTotalFinal(),
            clienteNome,
            fotografoIds
        ));

        log.atInfo()
            .addKeyValue("event_action", "agendamento.pagamento_final_registrado")
            .addKeyValue("agendamento_id", agendamento.getId())
            .addKeyValue("valor_total_final", agendamento.getValorTotalFinal())
            .addKeyValue("forma_pagamento", formaPagamento)
            .addKeyValue("tem_comprovante", temComprovante)
            .log("Pagamento final registrado");

        return agendamento;
    }

    private Agendamento buscarPorId(UUID id) {
        return agendamentoRepository.findByIdWithLock(id)
            .orElseThrow(() -> new AgendamentoNaoEncontradoException(id));
    }
}