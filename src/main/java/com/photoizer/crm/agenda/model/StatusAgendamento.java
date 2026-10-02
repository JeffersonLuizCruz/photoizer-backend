package com.photoizer.crm.agenda.model;

import com.photoizer.crm.agenda.exception.StatusAgendamentoInvalidoException;

import java.util.Map;
import java.util.Set;

/**
 * State Pattern — centraliza regras de transição de estado do agendamento.
 * Elimina validação condicional `if` espalhada no service.
 *
 * Ciclo de proposta/pré-reserva (novo fluxo único):
 *   PRE_RESERVA -> AGUARDANDO_APROVACAO -> PAGAMENTO_CONFIRMADO -> CONFIRMADO
 *
 * Ciclo operacional pós-aprovação:
 *   CONFIRMADO -> REALIZADO -> AGUARDANDO_PAGAMENTO_FINAL -> EM_EDICAO -> ... -> FINALIZADO
 */
public enum StatusAgendamento {
    PRE_RESERVA,
    AGUARDANDO_APROVACAO,
    PAGAMENTO_CONFIRMADO,
    CONFIRMADO,
    REALIZADO,
    AGUARDANDO_PAGAMENTO_FINAL,
    EM_EDICAO,
    FINALIZADO,
    CANCELADO,
    NO_SHOW;

    private static final Map<StatusAgendamento, Set<StatusAgendamento>> TRANSICOES = Map.ofEntries(
        Map.entry(PRE_RESERVA, Set.of(AGUARDANDO_APROVACAO, CANCELADO)),
        Map.entry(AGUARDANDO_APROVACAO, Set.of(PAGAMENTO_CONFIRMADO, CANCELADO)),
        Map.entry(PAGAMENTO_CONFIRMADO, Set.of(CONFIRMADO, CANCELADO)),
        Map.entry(CONFIRMADO, Set.of(REALIZADO, AGUARDANDO_PAGAMENTO_FINAL, CANCELADO, NO_SHOW)),
        Map.entry(REALIZADO, Set.of(AGUARDANDO_PAGAMENTO_FINAL, EM_EDICAO, CANCELADO, NO_SHOW)),
        Map.entry(AGUARDANDO_PAGAMENTO_FINAL, Set.of(EM_EDICAO, CANCELADO)),
        Map.entry(EM_EDICAO, Set.of(FINALIZADO, CANCELADO)),
        Map.entry(FINALIZADO, Set.of()),
        Map.entry(CANCELADO, Set.of()),
        Map.entry(NO_SHOW, Set.of())
    );

    public boolean podeTransicionarPara(StatusAgendamento novo) {
        return TRANSICOES.getOrDefault(this, Set.of()).contains(novo);
    }

    public void validarTransicao(StatusAgendamento novo) {
        if (!podeTransicionarPara(novo)) {
            throw new StatusAgendamentoInvalidoException(this, novo);
        }
    }

    /** Status em que a data ainda não está reservada (não ocupam a agenda). */
    public boolean isPreReserva() {
        return this == PRE_RESERVA || this == AGUARDANDO_APROVACAO || this == PAGAMENTO_CONFIRMADO;
    }
}
