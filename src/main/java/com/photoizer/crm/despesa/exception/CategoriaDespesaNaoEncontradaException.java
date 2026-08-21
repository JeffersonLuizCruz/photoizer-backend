package com.photoizer.crm.despesa.exception;

import java.util.UUID;

public class CategoriaDespesaNaoEncontradaException extends RuntimeException {

    public CategoriaDespesaNaoEncontradaException(UUID id) {
        super("Categoria de despesa não encontrada: " + id);
    }
}
