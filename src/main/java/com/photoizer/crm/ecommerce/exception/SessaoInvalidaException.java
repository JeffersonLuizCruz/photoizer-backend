package com.photoizer.crm.ecommerce.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.UnauthorizedException;

public class SessaoInvalidaException extends UnauthorizedException {

    public SessaoInvalidaException() {
        super(ErrorCode.SESSAO_INVALIDA, "Sessão inválida");
    }
}
