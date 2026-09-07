package com.photoizer.crm.cliente.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.NotFoundException;

import java.util.UUID;

public class ClienteNaoEncontradoException extends NotFoundException {

    public ClienteNaoEncontradoException(UUID id) {
        super(ErrorCode.CLIENTE_NAO_ENCONTRADO, "Cliente não encontrado: " + id);
    }
}
