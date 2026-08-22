package com.photoizer.crm.edicao.model;

import com.photoizer.crm.edicao.exception.StatusEdicaoInvalidoException;

import java.util.Map;
import java.util.Set;

/**
 * State Pattern — centraliza regras de transição de estado da edição.
 * Elimina validação condicional `if` espalhada no service.
 * Segue padrão do StatusCompraExtra (ecommerce).
 */
public enum StatusEdicao {
    AGUARDANDO_RAW,
    RAW_ENVIADOS,
    EM_EDICAO,
    EDICAO_CONCLUIDA;

    private static final Map<StatusEdicao, Set<StatusEdicao>> TRANSICOES = Map.of(
        AGUARDANDO_RAW, Set.of(RAW_ENVIADOS),
        RAW_ENVIADOS, Set.of(EM_EDICAO),
        EM_EDICAO, Set.of(EDICAO_CONCLUIDA),
        EDICAO_CONCLUIDA, Set.of()
    );

    public boolean podeTransicionarPara(StatusEdicao novo) {
        return TRANSICOES.getOrDefault(this, Set.of()).contains(novo);
    }

    public void validarTransicao(StatusEdicao novo) {
        if (!podeTransicionarPara(novo)) {
            throw new StatusEdicaoInvalidoException(
                String.format("Transição inválida: %s → %s", this, novo));
        }
    }
}
