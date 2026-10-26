package com.photoizer.crm.ecommerce.exception;

import com.photoizer.crm.shared.exception.ConflictException;
import com.photoizer.crm.shared.exception.ErrorCode;

public class GaleriaEmEdicaoException extends ConflictException {

    public GaleriaEmEdicaoException(String operacao) {
        super(ErrorCode.GALERIA_EM_EDICAO,
            "A galeria está em seleção/edição e não permite " + operacao + " neste momento.");
    }
}
