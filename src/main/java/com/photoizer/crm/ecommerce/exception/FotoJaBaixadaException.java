package com.photoizer.crm.ecommerce.exception;

import com.photoizer.crm.shared.exception.ConflictException;
import com.photoizer.crm.shared.exception.ErrorCode;

public class FotoJaBaixadaException extends ConflictException {

    public FotoJaBaixadaException() {
        super(ErrorCode.FOTO_JA_BAIXADA, "Foto já baixada não pode ser removida do pacote");
    }
}
