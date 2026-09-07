package com.photoizer.crm.despesa.exception;

import java.util.UUID;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.NotFoundException;

public class DespesaNaoEncontradaException extends NotFoundException {

    public DespesaNaoEncontradaException(UUID id) {
        super(ErrorCode.DESPESA_NAO_ENCONTRADA, "Despesa não encontrada: " + id);
    }
}
