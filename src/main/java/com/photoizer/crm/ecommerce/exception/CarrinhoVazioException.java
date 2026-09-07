package com.photoizer.crm.ecommerce.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.UnprocessableException;

public class CarrinhoVazioException extends UnprocessableException {

    public CarrinhoVazioException() {
        super(ErrorCode.CARRINHO_VAZIO, "Carrinho vazio");
    }
}
