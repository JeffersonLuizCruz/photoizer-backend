package com.photoizer.crm.agenda.model;

import com.photoizer.crm.agenda.exception.StatusAgendamentoInvalidoException;

import java.util.Map;
import java.util.Set;

/**
 * State Pattern — centraliza regras de transição de estado do agendamento.
 * Elimina validação condicional `if` espalhada no service.
 */
public enum StatusAgendamento {
    CONFIRMADO,
    REALIZADO,
    AGUARDANDO_PAGAMENTO_FINAL,
    EM_EDICAO,
    SELECAO_DAS_FOTOS,
    FOTOS_ENVIADAS_PARA_SELECAO,
    FOTOS_ENTREGUES,
    FINALIZADO,
    CANCELADO,
    NO_SHOW;

    private static final Map<StatusAgendamento, Set<StatusAgendamento>> TRANSICOES = Map.of(
        CONFIRMADO, Set.of(REALIZADO, AGUARDANDO_PAGAMENTO_FINAL, CANCELADO, NO_SHOW),
        REALIZADO, Set.of(AGUARDANDO_PAGAMENTO_FINAL, EM_EDICAO, CANCELADO, NO_SHOW),
        AGUARDANDO_PAGAMENTO_FINAL, Set.of(EM_EDICAO, CANCELADO),
        EM_EDICAO, Set.of(SELECAO_DAS_FOTOS, FOTOS_ENVIADAS_PARA_SELECAO, CANCELADO),
        SELECAO_DAS_FOTOS, Set.of(FOTOS_ENVIADAS_PARA_SELECAO, CANCELADO),
        FOTOS_ENVIADAS_PARA_SELECAO, Set.of(FOTOS_ENTREGUES, CANCELADO),
        FOTOS_ENTREGUES, Set.of(FINALIZADO, CANCELADO),
        FINALIZADO, Set.of(),
        CANCELADO, Set.of(),
        NO_SHOW, Set.of()
    );

    public boolean podeTransicionarPara(StatusAgendamento novo) {
        return TRANSICOES.getOrDefault(this, Set.of()).contains(novo);
    }

    public void validarTransicao(StatusAgendamento novo) {
        if (!podeTransicionarPara(novo)) {
            throw new StatusAgendamentoInvalidoException(this, novo);
        }
    }
}
