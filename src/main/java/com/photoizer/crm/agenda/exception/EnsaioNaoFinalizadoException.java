package com.photoizer.crm.agenda.exception;

public class EnsaioNaoFinalizadoException extends RuntimeException {

    public EnsaioNaoFinalizadoException() {
        super("Ensaio não finalizado. Para finalizar o ensaio é necessário registrar o comprovante de pagamento dos 70% restantes.");
    }

    public EnsaioNaoFinalizadoException(String message) {
        super(message);
    }
}