package com.photoizer.crm.ecommerce.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.UnprocessableException;

public class LimitePacoteExcedidoException extends UnprocessableException {

    public LimitePacoteExcedidoException(int limite) {
        super(ErrorCode.LIMITE_PACOTE_EXCEDIDO, "Limite do pacote excedido: máximo de " + limite + " foto(s) selecionada(s) no pacote");
    }
}
