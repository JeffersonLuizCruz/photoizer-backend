package com.photoizer.crm.bloqueio.service;

import com.photoizer.crm.bloqueio.exception.AgendamentoNoPeriodoException;
import com.photoizer.crm.bloqueio.exception.PeriodoInvalidoException;
import com.photoizer.crm.bloqueio.exception.PeriodoJaBloqueadoException;
import com.photoizer.crm.bloqueio.model.BloqueioAgenda;
import com.photoizer.crm.bloqueio.repository.BloqueioAgendaRepository;
import com.photoizer.crm.shared.exception.ForbiddenException;
import com.photoizer.crm.shared.exception.UnprocessableException;
import com.photoizer.crm.shared.port.AgendamentoOcupacaoPort;
import com.photoizer.crm.shared.port.UsuarioStaffPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BloqueioAgendaServiceTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final UUID PROFISSIONAL_ID = UUID.randomUUID();
    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 11, 20, 8, 0);
    private static final LocalDateTime FIM = LocalDateTime.of(2026, 11, 20, 18, 0);

    private final BloqueioAgendaRepository repository = mock(BloqueioAgendaRepository.class);
    private final AgendamentoOcupacaoPort agendamentoOcupacaoPort = mock(AgendamentoOcupacaoPort.class);
    private final UsuarioStaffPort usuarioStaffPort = mock(UsuarioStaffPort.class);
    private final BloqueioAgendaService service = new BloqueioAgendaService(
        repository, agendamentoOcupacaoPort, usuarioStaffPort);

    @Test
    @DisplayName("cria bloqueio global quando não há conflitos")
    void criaBloqueioGlobal() {
        when(repository.findAtivosAplicaveisNoPeriodo(any(), any(), any())).thenReturn(List.of());
        when(agendamentoOcupacaoPort.buscarConflitos(any(), any(), any())).thenReturn(List.of());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var bloqueio = service.criar(new BloqueioAgendaCommand(
            "Congresso", "Viagem", INICIO, FIM, null), USUARIO_ID);

        assertThat(bloqueio.getTitulo()).isEqualTo("Congresso");
        assertThat(bloqueio.isGlobal()).isTrue();
        assertThat(bloqueio.getCriadoPorId()).isEqualTo(USUARIO_ID);
        assertThat(bloqueio.isAtivo()).isTrue();
    }

    @Test
    @DisplayName("rejeita profissional inexistente ou inativo")
    void rejeitaProfissionalInativo() {
        when(usuarioStaffPort.existeStaffAtivo(PROFISSIONAL_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.criar(new BloqueioAgendaCommand(
            "Compromisso", null, INICIO, FIM, PROFISSIONAL_ID), USUARIO_ID))
            .isInstanceOf(UnprocessableException.class);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("rejeita período inválido")
    void rejeitaPeriodoInvalido() {
        assertThatThrownBy(() -> service.criar(new BloqueioAgendaCommand(
            "Compromisso", null, FIM, INICIO, null), USUARIO_ID))
            .isInstanceOf(PeriodoInvalidoException.class);
    }

    @Test
    @DisplayName("rejeita sobreposição com outro bloqueio aplicável")
    void rejeitaSobreposicaoComBloqueio() {
        var existente = bloqueio(USUARIO_ID, null);
        when(repository.findAtivosAplicaveisNoPeriodo(any(), any(), any())).thenReturn(List.of(existente));

        assertThatThrownBy(() -> service.criar(new BloqueioAgendaCommand(
            "Outro", null, INICIO, FIM, null), USUARIO_ID))
            .isInstanceOf(PeriodoJaBloqueadoException.class)
            .hasMessageContaining(existente.getTitulo());
    }

    @Test
    @DisplayName("rejeita sobreposição com ensaio ativo")
    void rejeitaSobreposicaoComAgendamento() {
        when(repository.findAtivosAplicaveisNoPeriodo(any(), any(), any())).thenReturn(List.of());
        when(agendamentoOcupacaoPort.buscarConflitos(any(), any(), any())).thenReturn(List.of(
            new AgendamentoOcupacaoPort.ConflitoAgendamento(
                UUID.randomUUID(), INICIO.plusHours(1), 60, "Estúdio")));

        assertThatThrownBy(() -> service.criar(new BloqueioAgendaCommand(
            "Compromisso", null, INICIO, FIM, null), USUARIO_ID))
            .isInstanceOf(AgendamentoNoPeriodoException.class);
    }

    @Test
    @DisplayName("somente o criador ou ADMIN atualiza o compromisso")
    void validaOwnershipNaAtualizacao() {
        var existente = bloqueio(USUARIO_ID, null);
        when(repository.findByIdAndAtivoTrue(existente.getId())).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> service.atualizar(
            existente.getId(),
            new BloqueioAgendaCommand("Alterado", null, INICIO, FIM, null),
            UUID.randomUUID(), false))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("cancelamento é lógico e preserva o histórico")
    void cancelamentoLogico() {
        var existente = bloqueio(USUARIO_ID, null);
        when(repository.findByIdAndAtivoTrue(existente.getId())).thenReturn(Optional.of(existente));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.cancelar(existente.getId(), USUARIO_ID, false);

        assertThat(existente.isAtivo()).isFalse();
        assertThat(existente.getCanceladoEm()).isNotNull();
        assertThat(existente.getCanceladoPorId()).isEqualTo(USUARIO_ID);
    }

    @Test
    @DisplayName("listagem por profissional inclui bloqueios globais")
    void listagemPorProfissionalUsaConsultaAplicavel() {
        when(repository.findAtivosAplicaveisNoPeriodo(any(), any(), eq(PROFISSIONAL_ID)))
            .thenReturn(List.of(bloqueio(USUARIO_ID, null)));

        var resultado = service.listar(INICIO, FIM, PROFISSIONAL_ID);

        assertThat(resultado).hasSize(1);
        verify(repository).findAtivosAplicaveisNoPeriodo(any(), any(), eq(PROFISSIONAL_ID));
    }

    private BloqueioAgenda bloqueio(UUID criadoPor, UUID profissionalId) {
        return BloqueioAgenda.builder()
            .id(UUID.randomUUID())
            .titulo("Congresso")
            .inicio(INICIO)
            .fim(FIM)
            .profissionalId(profissionalId)
            .criadoPorId(criadoPor)
            .ativo(true)
            .build();
    }
}
