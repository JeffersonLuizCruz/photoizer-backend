package com.photoizer.crm.foto.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.NotFoundException;

import java.util.UUID;

public class FotoEnsaioNaoEncontradaException extends NotFoundException {
    public FotoEnsaioNaoEncontradaException(UUID id) {
        super(ErrorCode.FOTO_ENSAIO_NAO_ENCONTRADA, "Foto não encontrada: " + id);
    }
}
