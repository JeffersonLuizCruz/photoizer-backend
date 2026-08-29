package com.photoizer.crm.foto.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class FotoEnsaioNaoEncontradaException extends RuntimeException {
    public FotoEnsaioNaoEncontradaException(UUID id) {
        super("Foto não encontrada: " + id);
    }
}
