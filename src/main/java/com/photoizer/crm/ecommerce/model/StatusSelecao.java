package com.photoizer.crm.ecommerce.model;

import com.photoizer.crm.ecommerce.exception.SelecaoNaoEditavelException;

import java.util.Map;
import java.util.Set;

/**
 * State Pattern: centraliza as transicoes validas da selecao enviada.
 */
public enum StatusSelecao {
    ENVIADA,
    EM_EDICAO,
    CONCLUIDA,
    PUBLICADA,
    CANCELADA;

    private static final Map<StatusSelecao, Set<StatusSelecao>> TRANSICOES = Map.of(
        ENVIADA, Set.of(EM_EDICAO, CANCELADA),
        EM_EDICAO, Set.of(CONCLUIDA, CANCELADA),
        CONCLUIDA, Set.of(PUBLICADA, EM_EDICAO, CANCELADA),
        PUBLICADA, Set.of(),
        CANCELADA, Set.of()
    );

    public boolean podeTransicionarPara(StatusSelecao proximo) {
        return TRANSICOES.getOrDefault(this, Set.of()).contains(proximo);
    }

    public void validarTransicao(StatusSelecao proximo) {
        if (!podeTransicionarPara(proximo)) {
            throw new SelecaoNaoEditavelException(this, proximo);
        }
    }

    public boolean estaAtiva() {
        return this == ENVIADA || this == EM_EDICAO || this == CONCLUIDA;
    }
}
