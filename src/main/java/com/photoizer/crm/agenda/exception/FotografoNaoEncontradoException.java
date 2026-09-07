package com.photoizer.crm.agenda.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.NotFoundException;

import java.util.UUID;

public class FotografoNaoEncontradoException extends NotFoundException {

    public FotografoNaoEncontradoException(UUID id) {
        super(ErrorCode.FOTOGRAFO_NAO_ENCONTRADO, "Fotógrafo não encontrado: " + id);
    }
}
