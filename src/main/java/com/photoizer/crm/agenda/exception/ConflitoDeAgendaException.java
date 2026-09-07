package com.photoizer.crm.agenda.exception;

import com.photoizer.crm.shared.exception.ConflictException;
import com.photoizer.crm.shared.exception.ErrorCode;

public class ConflitoDeAgendaException extends ConflictException {

    public ConflitoDeAgendaException(String message) {
        super(ErrorCode.CONFLITO_DE_AGENDA, message);
    }
}
