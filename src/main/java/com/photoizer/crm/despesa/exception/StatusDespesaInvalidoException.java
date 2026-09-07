package com.photoizer.crm.despesa.exception;

import com.photoizer.crm.despesa.model.StatusDespesa;
import com.photoizer.crm.shared.exception.ConflictException;
import com.photoizer.crm.shared.exception.ErrorCode;

public class StatusDespesaInvalidoException extends ConflictException {

    public StatusDespesaInvalidoException(StatusDespesa atual, String operacao) {
        super(ErrorCode.STATUS_DESPESA_INVALIDO, "Transição de status inválida: não é possível " + operacao +
              " a partir do status " + atual);
    }
}
