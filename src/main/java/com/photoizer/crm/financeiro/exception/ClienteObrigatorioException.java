package com.photoizer.crm.financeiro.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.UnprocessableException;

public class ClienteObrigatorioException extends UnprocessableException {
    public ClienteObrigatorioException() {
        super(ErrorCode.CLIENTE_OBRIGATORIO, "Informe um cliente para a receita");
    }
}
