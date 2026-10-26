package com.photoizer.crm.foto.exception;

import com.photoizer.crm.shared.exception.ConflictException;
import com.photoizer.crm.shared.exception.ErrorCode;

public class FotoNaoEditavelException extends ConflictException {

    public FotoNaoEditavelException(String motivo) {
        super(ErrorCode.FOTO_NAO_EDITAVEL,
            "A foto não pode receber uma versão editada: " + motivo);
    }
}
