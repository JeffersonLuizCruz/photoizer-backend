package com.photoizer.crm.foto.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.GoneException;

/**
 * 410 — os arquivos da foto foram removidos no expurgo da galeria.
 * O registro e o histórico continuam existindo.
 */
public class ArquivoExpurgadoException extends GoneException {

    public ArquivoExpurgadoException() {
        super(ErrorCode.ARQUIVO_EXPURGADO,
            "Os arquivos desta foto foram expurgados para liberar espaço.");
    }
}
