package com.photoizer.crm.agenda.exception;

import java.util.UUID;

public class PacoteNaoEncontradoException extends RuntimeException {

    public PacoteNaoEncontradoException(UUID id) {
        super("Pacote não encontrado: " + id);
    }
}
