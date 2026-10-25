package com.photoizer.crm.foto.exception;

import com.photoizer.crm.shared.exception.ConflictException;
import com.photoizer.crm.shared.exception.ErrorCode;

/**
 * Impede a remoção de fotos que já foram vendidas, estão em uma compra
 * pendente ou foram incluídas no pacote do ensaio.
 */
public class FotoVinculadaACompraException extends ConflictException {

    public FotoVinculadaACompraException(int quantidade) {
        super(ErrorCode.FOTO_VINCULADA_A_COMPRA,
            quantidade + " foto(s) não podem ser removidas por estarem vinculadas a uma compra ou ao pacote do ensaio");
    }
}
