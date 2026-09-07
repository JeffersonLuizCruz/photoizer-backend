package com.photoizer.crm.pacote.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.UnprocessableException;

import java.util.UUID;

public class PacoteInativoException extends UnprocessableException {

    public PacoteInativoException(UUID id) {
        super(ErrorCode.PACOTE_INATIVO, "Pacote inativo: " + id);
    }
}
