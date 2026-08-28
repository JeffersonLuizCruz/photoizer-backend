package com.photoizer.crm.financeiro.exception;

import java.util.UUID;

public class PacoteNaoEncontradoParaPreviewException extends RuntimeException {
    public PacoteNaoEncontradoParaPreviewException(UUID id) {
        super("Pacote não encontrado para preview financeiro: " + id);
    }
}
