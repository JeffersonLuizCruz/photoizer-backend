package com.photoizer.crm.agenda.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.NotFoundException;

public class PropostaNaoEncontradaException extends NotFoundException {

    public PropostaNaoEncontradaException(String token) {
        super(ErrorCode.PROPOSTA_NAO_ENCONTRADA, "Proposta não encontrada para o token informado.");
    }

    public PropostaNaoEncontradaException(java.util.UUID id) {
        super(ErrorCode.PROPOSTA_NAO_ENCONTRADA, "Proposta não encontrada: " + id);
    }
}
