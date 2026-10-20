package com.photoizer.crm.bloqueio.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.NotFoundException;

import java.util.UUID;

public class BloqueioNaoEncontradoException extends NotFoundException {

    public BloqueioNaoEncontradoException(UUID id) {
        super(ErrorCode.BLOQUEIO_NAO_ENCONTRADO, "Bloqueio de agenda não encontrado: " + id);
    }
}
