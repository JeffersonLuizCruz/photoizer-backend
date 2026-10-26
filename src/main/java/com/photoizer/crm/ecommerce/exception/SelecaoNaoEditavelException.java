package com.photoizer.crm.ecommerce.exception;

import com.photoizer.crm.ecommerce.model.StatusSelecao;
import com.photoizer.crm.shared.exception.ConflictException;
import com.photoizer.crm.shared.exception.ErrorCode;

public class SelecaoNaoEditavelException extends ConflictException {

    public SelecaoNaoEditavelException(StatusSelecao atual, StatusSelecao proximo) {
        super(ErrorCode.SELECAO_NAO_EDITAVEL,
            "Transição de seleção inválida: " + atual + " → " + proximo);
    }

    public SelecaoNaoEditavelException(String mensagem) {
        super(ErrorCode.SELECAO_NAO_EDITAVEL, mensagem);
    }
}
