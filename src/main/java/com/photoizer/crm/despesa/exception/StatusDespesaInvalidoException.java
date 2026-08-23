package com.photoizer.crm.despesa.exception;

import com.photoizer.crm.despesa.model.StatusDespesa;

public class StatusDespesaInvalidoException extends RuntimeException {

    public StatusDespesaInvalidoException(StatusDespesa atual, String operacao) {
        super("Transição de status inválida: não é possível " + operacao +
              " a partir do status " + atual);
    }
}
