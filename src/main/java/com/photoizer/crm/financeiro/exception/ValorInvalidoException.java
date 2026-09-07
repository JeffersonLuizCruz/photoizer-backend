package com.photoizer.crm.financeiro.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.UnprocessableException;

public class ValorInvalidoException extends UnprocessableException {
    public ValorInvalidoException(String message) {
        super(ErrorCode.VALOR_INVALIDO, message);
    }
}
