package com.photoizer.crm.ecommerce.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.NotFoundException;
import java.util.UUID;

public class GaleriaNaoEncontradaException extends NotFoundException {

    public GaleriaNaoEncontradaException(UUID token) {
        super(ErrorCode.GALERIA_NAO_ENCONTRADA, "Galeria não encontrada para o token: " + token);
    }

    public GaleriaNaoEncontradaException(String message) {
        super(ErrorCode.GALERIA_NAO_ENCONTRADA, message);
    }
}
