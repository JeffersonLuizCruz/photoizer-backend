package com.photoizer.crm.despesa.exception;

import com.photoizer.crm.shared.exception.ConflictException;
import com.photoizer.crm.shared.exception.ErrorCode;

public class CategoriaEmUsoException extends ConflictException {

    public CategoriaEmUsoException(String nomeCategoria) {
        super(ErrorCode.CATEGORIA_EM_USO, "Categoria '" + nomeCategoria + "' possui despesas vinculadas e foi inativada em vez de removida");
    }
}
