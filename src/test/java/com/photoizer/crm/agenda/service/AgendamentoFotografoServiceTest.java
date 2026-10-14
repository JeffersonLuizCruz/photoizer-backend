package com.photoizer.crm.agenda.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.AgendamentoFotografo;
import com.photoizer.crm.agenda.model.RepasseStatus;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoFotografoRepository;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.auth.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Repasses pendentes de propostas removidas/recusadas (agendamento CANCELADO)
 * não devem ser listados como obrigações ativas.
 */
class AgendamentoFotografoServiceTest {

    private final AgendamentoFotografoRepository agendamentoFotografoRepository =
        mock(AgendamentoFotografoRepository.class);
    private final AgendamentoRepository agendamentoRepository = mock(AgendamentoRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PartilhaService partilhaService = mock(PartilhaService.class);

    private final AgendamentoFotografoService service = new AgendamentoFotografoService(
        agendamentoFotografoRepository,
        agendamentoRepository,
        userRepository,
        partilhaService,
        new AgendamentoValoresCalculator());

    @Test
    @DisplayName("listarPendentes ignora repasses de agendamento cancelado")
    void listarPendentesIgnoraAgendamentoCancelado() {
        var linkAtivo = link(StatusAgendamento.FINALIZADO);
        var linkCancelado = link(StatusAgendamento.CANCELADO);
        when(agendamentoFotografoRepository.findByStatusWithAgendamento(RepasseStatus.PENDENTE))
            .thenReturn(List.of(linkAtivo, linkCancelado));

        assertThat(service.listarPendentes()).containsExactly(linkAtivo);
    }

    @Test
    @DisplayName("listarPendentesPorFotografo ignora repasses de agendamento cancelado")
    void listarPendentesPorFotografoIgnoraAgendamentoCancelado() {
        var fotografoId = UUID.randomUUID();
        var linkAtivo = link(StatusAgendamento.REALIZADO);
        var linkCancelado = link(StatusAgendamento.CANCELADO);
        when(agendamentoFotografoRepository.findByFotografoIdAndStatusWithAgendamento(
            fotografoId, RepasseStatus.PENDENTE))
            .thenReturn(List.of(linkAtivo, linkCancelado));

        assertThat(service.listarPendentesPorFotografo(fotografoId)).containsExactly(linkAtivo);
    }

    private AgendamentoFotografo link(StatusAgendamento statusAgendamento) {
        var agendamento = Agendamento.builder()
            .id(UUID.randomUUID())
            .status(statusAgendamento)
            .build();
        return AgendamentoFotografo.builder()
            .id(UUID.randomUUID())
            .agendamento(agendamento)
            .status(RepasseStatus.PENDENTE)
            .build();
    }
}
