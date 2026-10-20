package com.photoizer.crm.bloqueio.exception;

import com.photoizer.crm.shared.exception.ConflictException;
import com.photoizer.crm.shared.exception.ErrorCode;

public class PeriodoJaBloqueadoException extends ConflictException {

    public PeriodoJaBloqueadoException(String message) {
        super(ErrorCode.PERIODO_BLOQUEADO, message);
    }
}
