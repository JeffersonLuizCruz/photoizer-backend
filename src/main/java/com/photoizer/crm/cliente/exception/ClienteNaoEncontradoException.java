package com.photoizer.crm.cliente.exception;

import java.util.UUID;

public class ClienteNaoEncontradoException extends RuntimeException {

    public ClienteNaoEncontradoException(UUID id) {
        super("Cliente não encontrado: " + id);
    }
}
