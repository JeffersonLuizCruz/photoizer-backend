package com.photoizer.crm.despesa.exception;

public class CategoriaEmUsoException extends RuntimeException {

    public CategoriaEmUsoException(String nomeCategoria) {
        super("Categoria '" + nomeCategoria + "' possui despesas vinculadas e foi inativada em vez de removida");
    }
}
