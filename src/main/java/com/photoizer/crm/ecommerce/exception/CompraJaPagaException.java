package com.photoizer.crm.ecommerce.exception;

import com.photoizer.crm.shared.exception.ConflictException;
import com.photoizer.crm.shared.exception.ErrorCode;

public class CompraJaPagaException extends ConflictException {

    public CompraJaPagaException() {
        super(ErrorCode.COMPRA_JA_PAGA, "Compra já paga não pode ser cancelada");
    }
}
