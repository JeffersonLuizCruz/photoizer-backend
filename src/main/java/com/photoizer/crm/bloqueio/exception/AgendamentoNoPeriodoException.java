package com.photoizer.crm.bloqueio.exception;

import com.photoizer.crm.shared.exception.ConflictException;
import com.photoizer.crm.shared.exception.ErrorCode;

public class AgendamentoNoPeriodoException extends ConflictException {

    public AgendamentoNoPeriodoException(String message) {
        super(ErrorCode.CONFLITO_DE_AGENDA, message);
    }
}
