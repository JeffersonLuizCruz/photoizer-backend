package com.photoizer.crm.ecommerce.exception;

public class LimitePacoteExcedidoException extends RuntimeException {

    public LimitePacoteExcedidoException(int limite) {
        super("Limite do pacote excedido: máximo de " + limite + " foto(s) selecionada(s) no pacote");
    }
}
