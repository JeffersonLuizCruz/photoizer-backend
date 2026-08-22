package com.photoizer.crm.ecommerce.exception;

import java.util.UUID;

public class CompraNaoEncontradaException extends RuntimeException {

    public CompraNaoEncontradaException(UUID id) {
        super("Compra não encontrada: " + id);
    }
}
