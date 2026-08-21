package com.photoizer.crm.despesa.exception;

import java.util.UUID;

public class DespesaNaoEncontradaException extends RuntimeException {

    public DespesaNaoEncontradaException(UUID id) {
        super("Despesa não encontrada: " + id);
    }
}
