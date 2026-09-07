package com.photoizer.crm.ecommerce.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.GoneException;

public class TokenExpiradoException extends GoneException {
    public TokenExpiradoException(String message) {
        super(ErrorCode.TOKEN_EXPIRADO, message);
    }
}
