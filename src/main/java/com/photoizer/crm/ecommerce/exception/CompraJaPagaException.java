package com.photoizer.crm.ecommerce.exception;

public class CompraJaPagaException extends RuntimeException {

    public CompraJaPagaException() {
        super("Compra já paga não pode ser cancelada");
    }
}
