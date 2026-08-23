package com.photoizer.crm.despesa.exception;

public class CategoriaObrigatoriaException extends RuntimeException {

    public CategoriaObrigatoriaException() {
        super("Categoria é obrigatória");
    }
}
