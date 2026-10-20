package com.photoizer.crm.bloqueio.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.UnprocessableException;

public class PeriodoInvalidoException extends UnprocessableException {

    public PeriodoInvalidoException(String message) {
        super(ErrorCode.PERIODO_INVALIDO, message);
    }
}
