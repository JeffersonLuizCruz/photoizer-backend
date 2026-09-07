package com.photoizer.crm.foto.exception;

import com.photoizer.crm.foto.model.StatusFoto;
import com.photoizer.crm.shared.exception.ConflictException;
import com.photoizer.crm.shared.exception.ErrorCode;

public class StatusFotoInvalidoException extends ConflictException {
    public StatusFotoInvalidoException(StatusFoto atual, StatusFoto proximo) {
        super(ErrorCode.STATUS_FOTO_INVALIDO, "Transição de status inválida: " + atual + " → " + proximo);
    }
}
