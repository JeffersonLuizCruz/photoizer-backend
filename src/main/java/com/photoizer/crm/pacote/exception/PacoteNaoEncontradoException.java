package com.photoizer.crm.pacote.exception;

import java.util.UUID;

public class PacoteNaoEncontradoException extends RuntimeException {

    public PacoteNaoEncontradoException(UUID id) {
        super("Pacote não encontrado: " + id);
    }
}
