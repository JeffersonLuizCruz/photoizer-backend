package com.photoizer.crm.contrato.exception;

public class ContratoTokenExpiradoException extends RuntimeException {

    public ContratoTokenExpiradoException(String token) {
        super("O link do contrato expirou. Solicite um novo link ao fotógrafo.");
    }
}