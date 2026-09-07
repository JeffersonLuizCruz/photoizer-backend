package com.photoizer.crm.ecommerce.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.NotFoundException;
import java.util.UUID;

public class FotoNaoEncontradaException extends NotFoundException {

    public FotoNaoEncontradaException(UUID id) {
        super(ErrorCode.FOTO_NAO_ENCONTRADA, "Foto não encontrada: " + id);
    }
}
