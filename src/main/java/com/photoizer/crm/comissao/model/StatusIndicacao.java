package com.photoizer.crm.comissao.model;

/**
 * Representa os estados possíveis de uma comissão de indicação.
 *
 * Pattern: State Pattern simplificado — transições de estado são validadas
 * pelos métodos de domínio pagar() e cancelar() na entidade Indicacao,
 * garantindo que apenas transições válidas sejam permitidas.
 */
public enum StatusIndicacao {
    PENDENTE,
    PAGA,
    CANCELADA;

    public boolean podePagar() {
        return this == PENDENTE;
    }

    public boolean podeCancelar() {
        return this == PENDENTE;
    }
}
