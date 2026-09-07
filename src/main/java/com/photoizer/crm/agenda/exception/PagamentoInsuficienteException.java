package com.photoizer.crm.agenda.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.UnprocessableException;

public class PagamentoInsuficienteException extends UnprocessableException {

    public PagamentoInsuficienteException(String message) {
        super(ErrorCode.PAGAMENTO_INSUFICIENTE, message);
    }
}
