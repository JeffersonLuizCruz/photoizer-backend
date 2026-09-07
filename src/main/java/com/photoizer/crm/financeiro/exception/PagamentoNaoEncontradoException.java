package com.photoizer.crm.financeiro.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.NotFoundException;

import java.util.UUID;

public class PagamentoNaoEncontradoException extends NotFoundException {
    public PagamentoNaoEncontradoException(UUID id) {
        super(ErrorCode.PAGAMENTO_NAO_ENCONTRADO, "Pagamento não encontrado: " + id);
    }
}
