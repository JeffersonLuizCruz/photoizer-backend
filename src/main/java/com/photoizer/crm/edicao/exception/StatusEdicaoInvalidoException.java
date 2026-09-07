package com.photoizer.crm.edicao.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public class StatusEdicaoInvalidoException extends EdicaoBusinessException {
    public StatusEdicaoInvalidoException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.STATUS_EDICAO_INVALIDO, message);
    }
}
