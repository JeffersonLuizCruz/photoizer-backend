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

    /** Status que não ocupam a agenda (não geram conflito). */
    private static final List<StatusAgendamento> STATUS_IGNORADOS = List.of(
        StatusAgendamento.CANCELADO, StatusAgendamento.NO_SHOW,
        StatusAgendamento.PRE_RESERVA, StatusAgendamento.AGUARDANDO_APROVACAO,
        StatusAgendamento.PAGAMENTO_CONFIRMADO);

    private final AgendamentoRepository agendamentoRepository;

    public DisponibilidadeService(AgendamentoRepository agendamentoRepository) {
        this.agendamentoRepository = agendamentoRepository;
    }

    public DisponibilidadeResponse verificarDisponibilidade(LocalDate data, String hora, Integer duracaoMinutos,
                                                            UUID excluirAgendamentoId, Boolean bloqueiaDiaInteiro) {
        return verificarDisponibilidade(data, hora, duracaoMinutos, excluirAgendamentoId, bloqueiaDiaInteiro, null);
    }

    public DisponibilidadeResponse verificarDisponibilidade(LocalDate data, String hora, Integer duracaoMinutos,
                                                            UUID excluirAgendamentoId, Boolean bloqueiaDiaInteiro,
                                                            UUID fotografoId) {
        var inicioDia = data.atStartOfDay();
        var fimDia = data.atTime(23, 59, 59);
        var statusesIgnorados = STATUS_IGNORADOS;

        var conflitos = new ArrayList<DisponibilidadeResponse.Conflito>();

        if (fotografoId != null) {
            var bloqueiosExistentes = agendamentoRepository
                .findBloqueiaDiaInteiroByFotografoAndDataBetween(fotografoId, inicioDia, fimDia, statusesIgnorados);

            if (excluirAgendamentoId != null) {
                bloqueiosExistentes.removeIf(a -> a.getId().equals(excluirAgendamentoId));
            }

            if (!bloqueiosExistentes.isEmpty()) {
                for (var bloqueio : bloqueiosExistentes) {
                    conflitos.add(new DisponibilidadeResponse.Conflito(
                        bloqueio.getId(),
                        bloqueio.getDataHoraEnsaio().toLocalTime().format(HORA_FORMAT),
                        bloqueio.nomeCliente()
                    ));
                }
            }
        }

        List<Agendamento> agendamentosNoDia;
        if (fotografoId != null) {
            agendamentosNoDia = excluirAgendamentoId != null
                ? agendamentoRepository.findActiveByFotografoAndDataBetweenExcludingId(
                    fotografoId, inicioDia, fimDia, statusesIgnorados, excluirAgendamentoId)
                : agendamentoRepository.findActiveByFotografoAndDataBetween(
                    fotografoId, inicioDia, fimDia, statusesIgnorados);
        } else {
            agendamentosNoDia = excluirAgendamentoId != null
                ? agendamentoRepository.findActiveBetweenExcludingId(
                    inicioDia, fimDia, statusesIgnorados, excluirAgendamentoId)
                : agendamentoRepository.findByDataBetween(inicioDia, fimDia, statusesIgnorados);
        }

        if (Boolean.TRUE.equals(bloqueiaDiaInteiro)) {
            for (var existente : agendamentosNoDia) {
                conflitos.add(new DisponibilidadeResponse.Conflito(
                    existente.getId(),
                    existente.getDataHoraEnsaio().toLocalTime().format(HORA_FORMAT),
                    existente.nomeCliente()
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
                        existente.nomeCliente()
                    ));
                }
            }
        }

        return new DisponibilidadeResponse(conflitos.isEmpty(), conflitos);
    }

    public void validarConflitoAgenda(Pacote pacote, LocalDateTime dataHora, int duracao, String local) {
        validarConflitoAgenda(pacote, dataHora, duracao, local, ConflitoAgendaParams.semFotografo());
    }

    public void validarConflitoAgenda(Pacote pacote, LocalDateTime dataHora, int duracao, String local,
                                      ConflitoAgendaParams params) {
        if (params.fotografoId() != null) {
            validarPorFotografo(pacote, dataHora, duracao, local, params);
            return;
        }

        validarGeral(pacote, dataHora, duracao, local, params.excluirAgendamentoId());
    }

    private void validarGeral(Pacote pacote, LocalDateTime dataHora, int duracao, String local, UUID excluirId) {
        if (pacote.getBloqueiaDiaInteiro()) {
            var inicioDia = dataHora.toLocalDate().atStartOfDay();
            var fimDia = dataHora.toLocalDate().atTime(23, 59, 59);
            var conflito = excluirId != null
                ? agendamentoRepository.existsByDataHoraEnsaioBetweenAndStatusNotInAndIdNot(
                    inicioDia, fimDia, STATUS_IGNORADOS, excluirId)
                : agendamentoRepository.existsByDataHoraEnsaioBetweenAndStatusNotIn(
                    inicioDia, fimDia, STATUS_IGNORADOS);
            if (conflito) {
                throw new ConflitoDeAgendaException(
                    "Já existe um agendamento nesta data. O pacote selecionado bloqueia o dia inteiro.");
            }
            return;
        }

        var inicioDia = dataHora.toLocalDate().atStartOfDay();
        var fimDia = dataHora.toLocalDate().atTime(23, 59, 59);
        var agendamentosNoDia = agendamentoRepository.findActiveByLocalAndDataBetween(
            local, inicioDia, fimDia, STATUS_IGNORADOS);

        if (excluirId != null) {
            agendamentosNoDia.removeIf(a -> a.getId().equals(excluirId));
        }

        verificarSobreposicao(agendamentosNoDia, dataHora, duracao,
            "Já existe um agendamento neste horário e local: ");
    }

    private void validarPorFotografo(Pacote pacote, LocalDateTime dataHora, int duracao, String local,
                                     ConflitoAgendaParams params) {
        var fotografoId = params.fotografoId();
        var excluirId = params.excluirAgendamentoId();
        var inicioDia = dataHora.toLocalDate().atStartOfDay();
        var fimDia = dataHora.toLocalDate().atTime(23, 59, 59);

        var bloqueiosExistentes = agendamentoRepository
            .findBloqueiaDiaInteiroByFotografoAndDataBetween(fotografoId, inicioDia, fimDia, STATUS_IGNORADOS);

        if (excluirId != null) {
            bloqueiosExistentes.removeIf(a -> a.getId().equals(excluirId));
        }

        if (!bloqueiosExistentes.isEmpty()) {
            throw new ConflitoDeAgendaException(
                "Já existe um agendamento de dia inteiro nesta data para este fotógrafo.");
        }

        if (pacote.getBloqueiaDiaInteiro()) {
            var conflito = excluirId != null
                ? agendamentoRepository.existsByFotografoIdAndDataHoraEnsaioBetweenAndStatusNotInAndIdNot(
                    fotografoId, inicioDia, fimDia, STATUS_IGNORADOS, excluirId)
                : agendamentoRepository.existsByFotografoIdAndDataHoraEnsaioBetweenAndStatusNotIn(
                    fotografoId, inicioDia, fimDia, STATUS_IGNORADOS);
            if (conflito) {
                throw new ConflitoDeAgendaException(
                    "Já existe um agendamento nesta data para este fotógrafo. O pacote selecionado bloqueia o dia inteiro.");
            }
            return;
        }

        var statusesIgnorados = STATUS_IGNORADOS;
        var agendamentosNoDia = excluirId != null
            ? agendamentoRepository.findActiveByFotografoAndDataBetweenExcludingId(
                fotografoId, inicioDia, fimDia, statusesIgnorados, excluirId)
            : agendamentoRepository.findActiveByFotografoAndDataBetween(
                fotografoId, inicioDia, fimDia, statusesIgnorados);

        verificarSobreposicao(agendamentosNoDia, dataHora, duracao,
            "Já existe um agendamento neste horário para este fotógrafo: ");
    }

    private void verificarSobreposicao(List<Agendamento> agendamentos, LocalDateTime dataHora, int duracao,
                                       String mensagemPrefixo) {
        var novoFim = dataHora.plusMinutes(duracao);

        for (var existente : agendamentos) {
            var fimExistente = existente.getDataHoraEnsaio().plusMinutes(existente.getDuracaoMinutos());
            if (dataHora.isBefore(fimExistente) && novoFim.isAfter(existente.getDataHoraEnsaio())) {
                throw new ConflitoDeAgendaException(
                    mensagemPrefixo + existente.getDataHoraEnsaio() + " às " + fimExistente);
            }
        }
    }
}
