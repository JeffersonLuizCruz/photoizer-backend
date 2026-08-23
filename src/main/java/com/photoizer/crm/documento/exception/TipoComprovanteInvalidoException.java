package com.photoizer.crm.documento.exception;

public class TipoComprovanteInvalidoException extends RuntimeException {

    public TipoComprovanteInvalidoException(String valor) {
        super("Tipo de comprovante invalido: '" + valor + "'. Valores aceitos: entrada, final.");
    }
}
