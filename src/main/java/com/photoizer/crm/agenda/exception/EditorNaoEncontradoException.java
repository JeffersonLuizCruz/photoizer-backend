package com.photoizer.crm.agenda.exception;

import java.util.UUID;

public class EditorNaoEncontradoException extends RuntimeException {

    public EditorNaoEncontradoException(UUID id) {
        super("Editor não encontrado: " + id);
    }
}
