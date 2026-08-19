package com.photoizer.crm.agenda.service;

import com.photoizer.crm.agenda.event.AgendamentoCanceladoEvent;
import com.photoizer.crm.agenda.event.AgendamentoConfirmadoEvent;
import com.photoizer.crm.agenda.event.AgendamentoRealizadoEvent;
import com.photoizer.crm.agenda.event.PagamentoFinalRegistradoEvent;
import com.photoizer.crm.agenda.exception.AgendamentoNaoEncontradoException;
import com.photoizer.crm.agenda.exception.EnsaioNaoFinalizadoException;
import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.shared.storage.FileStorageService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@Transactional
public class AgendamentoStatusLifecycle {

    private final AgendamentoRepository agendamentoRepository;
    private final DisponibilidadeService disponibilidadeService;
    private final ApplicationEventPublisher eventPublisher;
    private final FileStorageService fileStorageService;

    public AgendamentoStatusLifecycle(AgendamentoRepository agendamentoRepository,
                                      DisponibilidadeService disponibilidadeService,
                                      ApplicationEventPublisher eventPublisher,
                                      FileStorageService fileStorageService) {
        this.agendamentoRepository = agendamentoRepository;
        this.disponibilidadeService = disponibilidadeService;
        this.eventPublisher = eventPublisher;
        this.fileStorageService = fileStorageService;
    }

    public Agendamento atualizarStatus(UUID id, String novoStatus) {
        var agendamento = buscarPorId(id);
        var status = StatusAgendamento.valueOf(novoStatus);
        agendamento.transicionarPara(status);

        if (status == StatusAgendamento.REALIZADO) {
            eventPublisher.publishEvent(new AgendamentoRealizadoEvent(
                agendamento.getId(),
                agendamento.getCliente().getId()
            ));
        }

        if (status == StatusAgendamento.CANCELADO || status == StatusAgendamento.NO_SHOW) {
            eventPublisher.publishEvent(new AgendamentoCanceladoEvent(agendamento.getId()));
        }

        return agendamentoRepository.save(agendamento);
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
        disponibilidadeService.validarConflitoAgenda(pacote, novaDataHora, duracao, agendamento.getLocalEnsaio());

        agendamento.reagendar(novaDataHora, duracao);

        agendamento = agendamentoRepository.save(agendamento);

        eventPublisher.publishEvent(new AgendamentoConfirmadoEvent(
            agendamento.getId(),
            agendamento.getCliente().getId()
        ));

        return agendamento;
    }

    public Agendamento toggleDestaque(UUID id) {
        var agendamento = buscarPorId(id);
        agendamento.alternarDestaque();
        return agendamentoRepository.save(agendamento);
    }

    public Agendamento registrarPagamentoFinal(UUID id, MultipartFile comprovante) {
        var agendamento = buscarPorId(id);

        if (agendamento.getStatus() != StatusAgendamento.REALIZADO
            && agendamento.getStatus() != StatusAgendamento.AGUARDANDO_PAGAMENTO_FINAL) {
            throw new EnsaioNaoFinalizadoException(
                "O agendamento precisa estar como REALIZADO ou AGUARDANDO_PAGAMENTO_FINAL para registrar o pagamento final. Status atual: " + agendamento.getStatus()
            );
        }

        if (comprovante == null || comprovante.isEmpty()) {
            throw new IllegalArgumentException("Comprovante de pagamento é obrigatório para finalizar o ensaio");
        }

        var url = fileStorageService.salvar(comprovante);
        agendamento.aplicarPagamentoFinal(url);

        agendamento = agendamentoRepository.save(agendamento);

        eventPublisher.publishEvent(new PagamentoFinalRegistradoEvent(
            agendamento.getId(),
            agendamento.getValorTotalFinal()
        ));

        return agendamento;
    }

    private Agendamento buscarPorId(UUID id) {
        return agendamentoRepository.findById(id)
            .orElseThrow(() -> new AgendamentoNaoEncontradoException(id));
    }
}