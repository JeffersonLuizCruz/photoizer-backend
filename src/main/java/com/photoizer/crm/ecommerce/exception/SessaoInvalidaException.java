package com.photoizer.crm.ecommerce.exception;

public class SessaoInvalidaException extends RuntimeException {

    public SessaoInvalidaException() {
        super("Sessão inválida");
    }
}
