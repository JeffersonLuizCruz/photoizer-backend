package com.photoizer.crm.agenda.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.NotFoundException;

import java.util.UUID;

public class EditorNaoEncontradoException extends NotFoundException {

    public EditorNaoEncontradoException(UUID id) {
        super(ErrorCode.EDITOR_NAO_ENCONTRADO, "Editor não encontrado: " + id);
    }
}
