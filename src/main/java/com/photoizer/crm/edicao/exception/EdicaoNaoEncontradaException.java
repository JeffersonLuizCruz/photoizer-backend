package com.photoizer.crm.edicao.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public class EdicaoNaoEncontradaException extends EdicaoBusinessException {
    public EdicaoNaoEncontradaException(String message) {
        super(HttpStatus.NOT_FOUND, ErrorCode.EDICAO_NAO_ENCONTRADA, message);
    }
}
