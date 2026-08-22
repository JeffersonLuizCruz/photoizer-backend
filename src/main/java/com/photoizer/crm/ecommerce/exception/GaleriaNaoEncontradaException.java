package com.photoizer.crm.ecommerce.exception;

import java.util.UUID;

public class GaleriaNaoEncontradaException extends RuntimeException {

    public GaleriaNaoEncontradaException(UUID token) {
        super("Galeria não encontrada para o token: " + token);
    }

    public GaleriaNaoEncontradaException(String message) {
        super(message);
    }
}
