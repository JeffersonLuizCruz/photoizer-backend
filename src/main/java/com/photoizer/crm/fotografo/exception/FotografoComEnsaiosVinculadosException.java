package com.photoizer.crm.fotografo.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.UnprocessableException;

/**
 * Exceção lançada quando tenta remover um fotógrafo que possui ensaios vinculados.
 * HTTP 422 Unprocessable Entity.
 *
 * Design Pattern: Exception Hierarchy — exceção semântica de domínio.
 * Motivo: substitui IllegalArgumentException genérica, permitindo
 * tratamento específico no GlobalExceptionHandler e distinguindo
 * erros de "operação não permitida" de outros erros.
 * Futuramente poderá ser migrada para BusinessException do shared.
 */
public class FotografoComEnsaiosVinculadosException extends UnprocessableException {

    public FotografoComEnsaiosVinculadosException(int count) {
        super(ErrorCode.FOTOGRAFO_COM_ENSAIOS_VINCULADOS, "Fotógrafo possui " + count + " ensaio(s) vinculado(s). Desative-o em vez de remover.");
    }
}
