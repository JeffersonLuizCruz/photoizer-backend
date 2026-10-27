package com.photoizer.crm.ecommerce.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.GoneException;

/**
 * 410 — a galeria foi arquivada: os arquivos não existem mais no servidor.
 * O link público deixa de exibir fotos e downloads.
 */
public class GaleriaArquivadaException extends GoneException {

    public GaleriaArquivadaException() {
        super(ErrorCode.GALERIA_ARQUIVADA,
            "Esta galeria foi arquivada. Entre em contato com o estúdio para solicitar as fotos novamente.");
    }
}
