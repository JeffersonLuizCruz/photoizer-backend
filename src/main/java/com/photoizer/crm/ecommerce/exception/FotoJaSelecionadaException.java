package com.photoizer.crm.ecommerce.exception;

import com.photoizer.crm.shared.exception.ConflictException;
import com.photoizer.crm.shared.exception.ErrorCode;

public class FotoJaSelecionadaException extends ConflictException {

    public FotoJaSelecionadaException(String message) {
        super(ErrorCode.FOTO_JA_SELECIONADA, message);
    }
}
