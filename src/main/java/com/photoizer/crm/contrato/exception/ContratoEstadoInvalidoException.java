package com.photoizer.crm.contrato.exception;

import com.photoizer.crm.shared.exception.ConflictException;
import com.photoizer.crm.shared.exception.ErrorCode;

public class ContratoEstadoInvalidoException extends ConflictException {

    public ContratoEstadoInvalidoException(String esperado, String atual) {
        super(ErrorCode.CONTRATO_ESTADO_INVALIDO, "Operação inválida para o estado atual do contrato. Esperado: "
            + esperado + ". Atual: " + atual);
    }
}