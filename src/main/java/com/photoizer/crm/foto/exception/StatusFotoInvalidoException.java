package com.photoizer.crm.foto.exception;

import com.photoizer.crm.foto.model.StatusFoto;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class StatusFotoInvalidoException extends RuntimeException {
    public StatusFotoInvalidoException(StatusFoto atual, StatusFoto proximo) {
        super("Transição de status inválida: " + atual + " → " + proximo);
    }
}
