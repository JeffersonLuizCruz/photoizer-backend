package com.photoizer.crm.contrato.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.NotFoundException;

import java.util.UUID;

public class ContratoNaoEncontradoException extends NotFoundException {

    public ContratoNaoEncontradoException(UUID id) {
        super(ErrorCode.CONTRATO_NAO_ENCONTRADO, "Contrato não encontrado: " + id);
    }

    public ContratoNaoEncontradoException(String token) {
        super(ErrorCode.CONTRATO_NAO_ENCONTRADO, "Contrato não encontrado para o token informado");
    }
}