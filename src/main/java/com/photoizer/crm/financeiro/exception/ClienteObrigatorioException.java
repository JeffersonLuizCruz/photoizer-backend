package com.photoizer.crm.financeiro.exception;

public class ClienteObrigatorioException extends RuntimeException {
    public ClienteObrigatorioException() {
        super("Informe um cliente para a receita");
    }
}
