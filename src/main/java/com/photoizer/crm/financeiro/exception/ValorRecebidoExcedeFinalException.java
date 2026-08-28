package com.photoizer.crm.financeiro.exception;

public class ValorRecebidoExcedeFinalException extends RuntimeException {
    public ValorRecebidoExcedeFinalException() {
        super("Valor recebido não pode ser maior que o valor final");
    }
}
