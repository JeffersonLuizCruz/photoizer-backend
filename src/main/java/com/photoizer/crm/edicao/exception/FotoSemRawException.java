package com.photoizer.crm.edicao.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public class FotoSemRawException extends EdicaoBusinessException {
    public FotoSemRawException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.FOTO_SEM_RAW, message);
    }
}
