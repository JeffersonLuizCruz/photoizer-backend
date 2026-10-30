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
        // ENVIADA -> PUBLICADA e usada apenas no modo ENTREGA_DIRETA, em que o
        // staff aprova o snapshot sem passar pelas etapas de edicao.
        ENVIADA, Set.of(EM_EDICAO, PUBLICADA, CANCELADA),
        EM_EDICAO, Set.of(CONCLUIDA, CANCELADA),
        CONCLUIDA, Set.of(PUBLICADA, EM_EDICAO, CANCELADA),
        // PUBLICADA -> CANCELADA só é usado na reabertura de uma seleção
        // ENTREGA_DIRETA aprovada, enquanto não houver download ou venda.
        PUBLICADA, Set.of(CANCELADA),
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
