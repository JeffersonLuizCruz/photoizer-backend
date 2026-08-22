package com.photoizer.crm.ecommerce.exception;

public class FotoJaBaixadaException extends RuntimeException {

    public FotoJaBaixadaException() {
        super("Foto já baixada não pode ser removida do pacote");
    }
}
