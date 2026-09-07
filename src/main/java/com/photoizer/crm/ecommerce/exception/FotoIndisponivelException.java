package com.photoizer.crm.ecommerce.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.UnprocessableException;

public class FotoIndisponivelException extends UnprocessableException {

    public FotoIndisponivelException(String message) {
        super(ErrorCode.FOTO_INDISPONIVEL, message);
    }
}
