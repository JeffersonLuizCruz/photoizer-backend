package com.photoizer.crm.financeiro.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.UnprocessableException;

public class OperacaoNaoPermitidaException extends UnprocessableException {
    public OperacaoNaoPermitidaException(String message) {
        super(ErrorCode.OPERACAO_NAO_PERMITIDA, message);
    }
}
