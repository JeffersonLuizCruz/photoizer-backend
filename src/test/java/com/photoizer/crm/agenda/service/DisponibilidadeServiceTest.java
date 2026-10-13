package com.photoizer.crm.agenda.service;

import com.photoizer.crm.agenda.exception.ConflitoDeAgendaException;
import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.pacote.model.Pacote;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regressão: um ensaio FINALIZADO não deve ocupar a agenda (não gera "Horário indisponível"),
 * enquanto status ainda ativos (ex.: CONFIRMADO) continuam bloqueando o horário.
 *
 * O repositório é substituído por um stub que aplica o filtro de status ignorados recebido
 * pela service, reproduzindo a semântica do `status NOT IN :statusesIgnorados` do banco.
 */
class DisponibilidadeServiceTest {

    private static final UUID FOTOGRAFO_ID = UUID.randomUUID();
    private static final LocalDate DATA = LocalDate.of(2026, 3, 10);
    private static final String HORA = "08:00";

    private final AgendamentoRepository repository = mock(AgendamentoRepository.class);
    private final DisponibilidadeService service = new DisponibilidadeService(repository);

    @Test
    @DisplayName("verificarDisponibilidade ignora ensaio FINALIZADO no mesmo horário")
    void ensaioFinalizadoNaoGeraConflito() {
        stubAgendaDoDia(agendamento(StatusAgendamento.FINALIZADO));

        var resultado = service.verificarDisponibilidade(DATA, HORA, 60, null, false, FOTOGRAFO_ID);

        assertThat(resultado.disponivel()).isTrue();
        assertThat(resultado.conflitos()).isEmpty();
    }

    @Test
    @DisplayName("verificarDisponibilidade mantém conflito para ensaio CONFIRMADO")
    void ensaioConfirmadoAindaGeraConflito() {
        stubAgendaDoDia(agendamento(StatusAgendamento.CONFIRMADO));

        var resultado = service.verificarDisponibilidade(DATA, HORA, 60, null, false, FOTOGRAFO_ID);

        assertThat(resultado.disponivel()).isFalse();
        assertThat(resultado.conflitos()).hasSize(1);
    }

    @Test
    @DisplayName("validarConflitoAgenda libera horário quando o ensaio está FINALIZADO")
    void validacaoNaoFalhaParaEnsaioFinalizado() {
        stubAgendaDoDia(agendamento(StatusAgendamento.FINALIZADO));

        assertThatCode(() -> service.validarConflitoAgenda(
            pacote(), DATA.atTime(8, 0), 60, "Estúdio", ConflitoAgendaParams.paraCriacao(FOTOGRAFO_ID)))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validarConflitoAgenda ainda barra ensaio CONFIRMADO no mesmo horário")
    void validacaoFalhaParaEnsaioConfirmado() {
        stubAgendaDoDia(agendamento(StatusAgendamento.CONFIRMADO));

        assertThatThrownBy(() -> service.validarConflitoAgenda(
            pacote(), DATA.atTime(8, 0), 60, "Estúdio", ConflitoAgendaParams.paraCriacao(FOTOGRAFO_ID)))
            .isInstanceOf(ConflitoDeAgendaException.class);
    }

    private void stubAgendaDoDia(Agendamento... agendamentos) {
        var existentes = List.of(agendamentos);
        when(repository.findActiveByFotografoAndDataBetween(eq(FOTOGRAFO_ID), any(), any(), anyList()))
            .thenAnswer(inv -> filtrarIgnorados(existentes, inv.getArgument(3)));
        when(repository.findBloqueiaDiaInteiroByFotografoAndDataBetween(eq(FOTOGRAFO_ID), any(), any(), anyList()))
            .thenReturn(List.of());
    }

    private List<Agendamento> filtrarIgnorados(List<Agendamento> existentes, List<StatusAgendamento> ignorados) {
        return existentes.stream().filter(a -> !ignorados.contains(a.getStatus())).toList();
    }

    private Agendamento agendamento(StatusAgendamento status) {
        return Agendamento.builder()
            .id(UUID.randomUUID())
            .status(status)
            .dataHoraEnsaio(DATA.atTime(8, 0))
            .duracaoMinutos(60)
            .localEnsaio("Estúdio")
            .build();
    }

    private Pacote pacote() {
        return Pacote.builder()
            .id(UUID.randomUUID())
            .nome("Pacote")
            .valorBase(new BigDecimal("1000.00"))
            .bloqueiaDiaInteiro(false)
            .build();
    }
}
