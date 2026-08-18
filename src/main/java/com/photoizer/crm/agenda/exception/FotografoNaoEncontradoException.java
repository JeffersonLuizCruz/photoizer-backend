package com.photoizer.crm.agenda.exception;

import java.util.UUID;

public class FotografoNaoEncontradoException extends RuntimeException {

    public FotografoNaoEncontradoException(UUID id) {
        super("Fotógrafo não encontrado: " + id);
    }
}