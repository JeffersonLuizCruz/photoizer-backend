package com.photoizer.crm.despesa.exception;

import com.photoizer.crm.shared.exception.ConflictException;
import com.photoizer.crm.shared.exception.ErrorCode;

public class CategoriaDuplicadaException extends ConflictException {

    public CategoriaDuplicadaException(String nome) {
        super(ErrorCode.CATEGORIA_DUPLICADA, "Já existe uma categoria de despesa com esse nome: " + nome);
    }
}
