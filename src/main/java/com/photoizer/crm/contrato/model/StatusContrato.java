package com.photoizer.crm.contrato.model;

import com.photoizer.crm.contrato.exception.ContratoEstadoInvalidoException;

/**
 * Enum que representa os estados do ciclo de vida de um contrato.
 *
 * Pattern: State Machine encapsulada no enum — cada transicao ilegal
 * gera excecao com mensagem clara, sem depender de if espalhado no service.
 * O enum e a autoridade sobre quais transicoes sao validas.
 */
public enum StatusContrato {
    RASCUNHO,
    PUBLICADO,
    ASSINADO_PELO_CLIENTE,
    PAGAMENTO_CONFIRMADO,
    APROVADO,
    DEVOLVIDO,
    CANCELADO,
    EXPIRADO;

    /**
     * Valida se a transicao para o destino e permitida.
     * Lancam ContratoEstadoInvalidoException caso nao seja.
     */
    public void validarTransicaoPara(StatusContrato destino) {
        if (!this.podeTransitarPara(destino)) {
            throw new ContratoEstadoInvalidoException(destino.name(), this.name());
        }
    }

    /**
     * Verifica se a transicao e permitida (sem lancar excecao).
     */
    public boolean podeTransitarPara(StatusContrato destino) {
        return switch (this) {
            case RASCUNHO -> destino == PUBLICADO;
            case PUBLICADO -> destino == ASSINADO_PELO_CLIENTE || destino == CANCELADO || destino == EXPIRADO;
            case ASSINADO_PELO_CLIENTE -> destino == PAGAMENTO_CONFIRMADO || destino == DEVOLVIDO || destino == CANCELADO;
            case PAGAMENTO_CONFIRMADO -> destino == APROVADO || destino == DEVOLVIDO || destino == CANCELADO;
            case APROVADO -> false; // estado final (agendamento ja criado)
            case DEVOLVIDO -> destino == PUBLICADO || destino == CANCELADO; // pode re-publicar apos devolucao
            case CANCELADO -> destino == PUBLICADO; // pode re-publicar apos cancelamento
            case EXPIRADO -> destino == PUBLICADO; // pode re-publicar apos expiracao
        };
    }

    public boolean podePublicar() {
        return this == RASCUNHO || this == PUBLICADO || this == CANCELADO || this == EXPIRADO;
    }

    public boolean podeConfirmarPagamento() {
        return this == ASSINADO_PELO_CLIENTE;
    }

    public boolean podeAprovar() {
        return this == PAGAMENTO_CONFIRMADO;
    }

    public boolean podeDevolver() {
        return this == ASSINADO_PELO_CLIENTE || this == PAGAMENTO_CONFIRMADO;
    }

    public boolean podeCancelar() {
        return this != APROVADO && this != CANCELADO;
    }
}
