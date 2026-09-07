package com.photoizer.crm.financeiro.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.NotFoundException;

import java.util.UUID;

public class PacoteNaoEncontradoParaPreviewException extends NotFoundException {
    public PacoteNaoEncontradoParaPreviewException(UUID id) {
        super(ErrorCode.PACOTE_NAO_ENCONTRADO_PARA_PREVIEW, "Pacote não encontrado para preview financeiro: " + id);
    }
}
