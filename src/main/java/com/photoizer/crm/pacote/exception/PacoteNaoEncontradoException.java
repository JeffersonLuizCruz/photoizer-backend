package com.photoizer.crm.pacote.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.NotFoundException;

import java.util.UUID;

public class PacoteNaoEncontradoException extends NotFoundException {

    public PacoteNaoEncontradoException(UUID id) {
        super(ErrorCode.PACOTE_NAO_ENCONTRADO, "Pacote não encontrado: " + id);
    }
}
