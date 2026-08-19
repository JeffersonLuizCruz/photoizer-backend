package com.photoizer.crm.agenda.service;

import com.photoizer.crm.agenda.api.DisponibilidadeResponse;
import com.photoizer.crm.agenda.exception.ConflitoDeAgendaException;
import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.pacote.model.Pacote;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class DisponibilidadeService {

    private static final DateTimeFormatter HORA_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final AgendamentoRepository agendamentoRepository;

    public DisponibilidadeService(AgendamentoRepository agendamentoRepository) {
        this.agendamentoRepository = agendamentoRepository;
    }

    public DisponibilidadeResponse verificarDisponibilidade(LocalDate data, String hora, Integer duracaoMinutos,
                                                            UUID excluirAgendamentoId, Boolean bloqueiaDiaInteiro) {
        var inicioDia = data.atStartOfDay();
        var fimDia = data.atTime(23, 59, 59);
        var statusesIgnorados = List.of(StatusAgendamento.CANCELADO, StatusAgendamento.NO_SHOW);

        List<Agendamento> agendamentosNoDia;
        if (excluirAgendamentoId != null) {
            agendamentosNoDia = agendamentoRepository.findActiveBetweenExcludingId(
                inicioDia, fimDia, statusesIgnorados, excluirAgendamentoId);
        } else {
            agendamentosNoDia = agendamentoRepository.findByDataBetween(inicioDia, fimDia, statusesIgnorados);
        }

        var conflitos = new ArrayList<DisponibilidadeResponse.Conflito>();

        if (Boolean.TRUE.equals(bloqueiaDiaInteiro)) {
            for (var existente : agendamentosNoDia) {
                conflitos.add(new DisponibilidadeResponse.Conflito(
                    existente.getId(),
                    existente.getDataHoraEnsaio().toLocalTime().format(HORA_FORMAT),
                    existente.getCliente().getNome()
                ));
            }
        } else {
            var time = LocalTime.parse(hora, HORA_FORMAT);
            var dataHora = LocalDateTime.of(data, time);
            var duracao = duracaoMinutos != null ? duracaoMinutos : 60;
            var novoFim = dataHora.plusMinutes(duracao);

            for (var existente : agendamentosNoDia) {
                var fimExistente = existente.getDataHoraEnsaio().plusMinutes(existente.getDuracaoMinutos());
                if (dataHora.isBefore(fimExistente) && novoFim.isAfter(existente.getDataHoraEnsaio())) {
                    conflitos.add(new DisponibilidadeResponse.Conflito(
                        existente.getId(),
                        existente.getDataHoraEnsaio().toLocalTime().format(HORA_FORMAT),
                        existente.getCliente().getNome()
                    ));
                }
            }
        }

        return new DisponibilidadeResponse(conflitos.isEmpty(), conflitos);
    }

    public void validarConflitoAgenda(Pacote pacote, LocalDateTime dataHora, int duracao, String local, UUID excluirId) {
        if (pacote.getBloqueiaDiaInteiro()) {
            var inicioDia = dataHora.toLocalDate().atStartOfDay();
            var fimDia = dataHora.toLocalDate().atTime(23, 59, 59);
            var conflito = agendamentoRepository.existsByDataHoraEnsaioBetweenAndStatusNotAndIdNot(
                inicioDia, fimDia, StatusAgendamento.CANCELADO, excluirId);
            if (conflito) {
                throw new ConflitoDeAgendaException(
                    "Já existe um agendamento nesta data. O pacote selecionado bloqueia o dia inteiro.");
            }
            return;
        }

        var inicioDia = dataHora.toLocalDate().atStartOfDay();
        var fimDia = dataHora.toLocalDate().atTime(23, 59, 59);
        var statusesIgnorados = List.of(StatusAgendamento.CANCELADO, StatusAgendamento.NO_SHOW);
        var agendamentosNoDia = agendamentoRepository.findActiveBetweenExcludingId(
            inicioDia, fimDia, statusesIgnorados, excluirId);

        var novoFim = dataHora.plusMinutes(duracao);

        for (var existente : agendamentosNoDia) {
            var fimExistente = existente.getDataHoraEnsaio()
                .plusMinutes(existente.getDuracaoMinutos());
            if (dataHora.isBefore(fimExistente) && novoFim.isAfter(existente.getDataHoraEnsaio())) {
                throw new ConflitoDeAgendaException(
                    "Já existe um agendamento neste horário e local: "
                    + existente.getDataHoraEnsaio() + " às " + fimExistente);
            }
        }
    }

    public void validarConflitoAgenda(Pacote pacote, LocalDateTime dataHora, int duracao, String local) {
        if (pacote.getBloqueiaDiaInteiro()) {
            var inicioDia = dataHora.toLocalDate().atStartOfDay();
            var fimDia = dataHora.toLocalDate().atTime(23, 59, 59);
            var conflito = agendamentoRepository.existsByDataHoraEnsaioBetweenAndStatusNot(
                inicioDia, fimDia, StatusAgendamento.CANCELADO);
            if (conflito) {
                throw new ConflitoDeAgendaException(
                    "Já existe um agendamento nesta data. O pacote selecionado bloqueia o dia inteiro.");
            }
            return;
        }

        var inicioDia = dataHora.toLocalDate().atStartOfDay();
        var fimDia = dataHora.toLocalDate().atTime(23, 59, 59);
        var statusesIgnorados = List.of(StatusAgendamento.CANCELADO, StatusAgendamento.NO_SHOW);
        var agendamentosNoDia = agendamentoRepository.findActiveByLocalAndDataBetween(
            local, inicioDia, fimDia, statusesIgnorados);

        var novoFim = dataHora.plusMinutes(duracao);

        for (var existente : agendamentosNoDia) {
            var fimExistente = existente.getDataHoraEnsaio()
                .plusMinutes(existente.getDuracaoMinutos());
            if (dataHora.isBefore(fimExistente) && novoFim.isAfter(existente.getDataHoraEnsaio())) {
                throw new ConflitoDeAgendaException(
                    "Já existe um agendamento neste horário e local: "
                    + existente.getDataHoraEnsaio() + " às " + fimExistente);
            }
        }
    }
}