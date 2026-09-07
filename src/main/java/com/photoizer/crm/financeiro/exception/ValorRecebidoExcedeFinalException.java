package com.photoizer.crm.financeiro.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.UnprocessableException;

public class ValorRecebidoExcedeFinalException extends UnprocessableException {
    public ValorRecebidoExcedeFinalException() {
        super(ErrorCode.VALOR_RECEBIDO_EXCEDE_FINAL, "Valor recebido não pode ser maior que o valor final");
    }
}
