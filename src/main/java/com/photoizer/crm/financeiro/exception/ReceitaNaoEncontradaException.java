package com.photoizer.crm.financeiro.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.NotFoundException;

import java.util.UUID;

public class ReceitaNaoEncontradaException extends NotFoundException {
    public ReceitaNaoEncontradaException(UUID id) {
        super(ErrorCode.RECEITA_NAO_ENCONTRADA, "Receita não encontrada: " + id);
    }
}
