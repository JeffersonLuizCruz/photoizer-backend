package com.photoizer.crm.despesa.exception;

public class CategoriaDuplicadaException extends RuntimeException {

    public CategoriaDuplicadaException(String nome) {
        super("Já existe uma categoria de despesa com esse nome: " + nome);
    }
}
