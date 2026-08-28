package com.photoizer.crm.agenda.exception;

public class ComprovanteObrigatorioException extends RuntimeException {
    public ComprovanteObrigatorioException() {
        super("Comprovante de pagamento é obrigatório para finalizar o ensaio");
    }
}
