package com.photoizer.crm.contrato.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.GoneException;

public class ContratoTokenExpiradoException extends GoneException {

    public ContratoTokenExpiradoException(String token) {
        super(ErrorCode.CONTRATO_TOKEN_EXPIRADO, "O link do contrato expirou. Solicite um novo link ao fotógrafo.");
    }
}