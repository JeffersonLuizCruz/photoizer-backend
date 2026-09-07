package com.photoizer.crm.despesa.exception;

import java.util.UUID;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.NotFoundException;

public class CategoriaDespesaNaoEncontradaException extends NotFoundException {

    public CategoriaDespesaNaoEncontradaException(UUID id) {
        super(ErrorCode.CATEGORIA_DESPESA_NAO_ENCONTRADA, "Categoria de despesa não encontrada: " + id);
    }
}
