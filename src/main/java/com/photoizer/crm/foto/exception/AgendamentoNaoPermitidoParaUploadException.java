package com.photoizer.crm.foto.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class AgendamentoNaoPermitidoParaUploadException extends RuntimeException {
    public AgendamentoNaoPermitidoParaUploadException() {
        super("Agendamento não está em status permitido para upload de fotos");
    }
}
