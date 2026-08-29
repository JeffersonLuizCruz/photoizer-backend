package com.photoizer.crm.foto.model;

import com.photoizer.crm.foto.exception.StatusFotoInvalidoException;

/**
 * PATTERN: State Pattern
 * Centraliza regras de transição de status no enum, eliminando
 * if espalhado no service. Segue o padrão adotado em StatusCompraExtra
 * e StatusEdicao.
 */
public enum StatusFoto {
    INEDITA,
    PUBLICADA,
    AGUARDANDO_COMPROVANTE,
    AGUARDANDO_CONFIRMACAO,
    PAGA;

    /**
     * Verifica se a transição para o status indicado é válida.
     */
    public boolean podeTransicionarPara(StatusFoto proximo) {
        return switch (this) {
            case INEDITA -> proximo == PUBLICADA;
            case PUBLICADA -> proximo == AGUARDANDO_COMPROVANTE || proximo == INEDITA;
            case AGUARDANDO_COMPROVANTE -> proximo == AGUARDANDO_CONFIRMACAO || proximo == PUBLICADA;
            case AGUARDANDO_CONFIRMACAO -> proximo == PAGA || proximo == PUBLICADA;
            case PAGA -> false;
        };
    }

    /**
     * Transiciona para o status indicado, lançando exceção se inválido.
     */
    public StatusFoto transicionarPara(StatusFoto proximo) {
        if (!podeTransicionarPara(proximo)) {
            throw new StatusFotoInvalidoException(this, proximo);
        }
        return proximo;
    }
}
