package com.photoizer.crm.ecommerce.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.NotFoundException;

import java.util.UUID;

public class SelecaoNaoEncontradaException extends NotFoundException {

    public SelecaoNaoEncontradaException(UUID selecaoId) {
        super(ErrorCode.SELECAO_NAO_ENCONTRADA, "Seleção não encontrada: " + selecaoId);
    }
}
