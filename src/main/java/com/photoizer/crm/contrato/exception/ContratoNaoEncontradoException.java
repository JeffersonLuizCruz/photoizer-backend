package com.photoizer.crm.contrato.exception;

import java.util.UUID;

public class ContratoNaoEncontradoException extends RuntimeException {

    public ContratoNaoEncontradoException(UUID id) {
        super("Contrato não encontrado: " + id);
    }

    public ContratoNaoEncontradoException(String token) {
        super("Contrato não encontrado para o token informado");
    }
}