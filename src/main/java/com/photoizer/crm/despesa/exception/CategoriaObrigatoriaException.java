package com.photoizer.crm.despesa.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.UnprocessableException;

public class CategoriaObrigatoriaException extends UnprocessableException {

    public CategoriaObrigatoriaException() {
        super(ErrorCode.CATEGORIA_OBRIGATORIA, "Categoria é obrigatória");
    }
}
