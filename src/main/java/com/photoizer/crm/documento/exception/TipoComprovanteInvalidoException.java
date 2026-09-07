package com.photoizer.crm.documento.exception;

import com.photoizer.crm.shared.exception.BadRequestException;
import com.photoizer.crm.shared.exception.ErrorCode;

public class TipoComprovanteInvalidoException extends BadRequestException {

    public TipoComprovanteInvalidoException(String valor) {
        super(ErrorCode.TIPO_COMPROVANTE_INVALIDO, "Tipo de comprovante invalido: '" + valor + "'. Valores aceitos: entrada, final.");
    }
}
