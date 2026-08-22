package com.photoizer.crm.ecommerce.exception;

public class CarrinhoVazioException extends RuntimeException {

    public CarrinhoVazioException() {
        super("Carrinho vazio");
    }
}
