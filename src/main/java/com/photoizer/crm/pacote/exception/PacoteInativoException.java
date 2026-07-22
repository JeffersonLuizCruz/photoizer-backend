package com.photoizer.crm.pacote.exception;

import java.util.UUID;

public class PacoteInativoException extends RuntimeException {

    public PacoteInativoException(UUID id) {
        super("Pacote inativo: " + id);
    }
}
