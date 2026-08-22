package com.photoizer.crm.ecommerce.exception;

import java.util.UUID;

public class FotoNaoEncontradaException extends RuntimeException {

    public FotoNaoEncontradaException(UUID id) {
        super("Foto não encontrada: " + id);
    }
}
