package com.photoizer.crm.edicao.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public class FotoEdicaoNaoEncontradaException extends EdicaoBusinessException {
    public FotoEdicaoNaoEncontradaException(String message) {
        super(HttpStatus.NOT_FOUND, ErrorCode.FOTO_EDICAO_NAO_ENCONTRADA, message);
    }
}
