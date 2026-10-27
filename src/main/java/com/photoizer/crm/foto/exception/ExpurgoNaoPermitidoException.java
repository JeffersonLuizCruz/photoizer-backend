package com.photoizer.crm.foto.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.UnprocessableException;

/**
 * 422 — o expurgo não é permitido no estado atual da galeria
 * (ensaio não finalizado, galeria já arquivada ou com pendências).
 */
public class ExpurgoNaoPermitidoException extends UnprocessableException {

    public ExpurgoNaoPermitidoException(String message) {
        super(ErrorCode.EXPURGO_NAO_PERMITIDO, message);
    }

    public ExpurgoNaoPermitidoException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
