package com.photoizer.crm.contrato.exception;

public class ContratoEstadoInvalidoException extends RuntimeException {

    public ContratoEstadoInvalidoException(String esperado, String atual) {
        super("Operação inválida para o estado atual do contrato. Esperado: "
            + esperado + ". Atual: " + atual);
    }
}