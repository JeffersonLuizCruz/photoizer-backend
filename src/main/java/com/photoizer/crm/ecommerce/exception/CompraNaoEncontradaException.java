package com.photoizer.crm.ecommerce.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.NotFoundException;
import java.util.UUID;

public class CompraNaoEncontradaException extends NotFoundException {

    public CompraNaoEncontradaException(UUID id) {
        super(ErrorCode.COMPRA_NAO_ENCONTRADA, "Compra não encontrada: " + id);
    }
}
