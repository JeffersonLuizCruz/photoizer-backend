package com.photoizer.crm.ecommerce.model;

/**
 * Modo de operacao da galeria.
 *
 * <ul>
 *   <li>{@link #DIRETO}: fluxo legado desativado. Mantido apenas para leitura de
 *       galerias antigas que ainda nao foram migradas para {@link #COM_EDICAO}.</li>
 *   <li>{@link #COM_EDICAO}: cliente envia selecao, staff edita e publica a
 *       versao editada antes da comercializacao. Unico modo aceito para novas
 *       galerias.</li>
 * </ul>
 */
public enum ModoGaleria {
    DIRETO,
    COM_EDICAO
}
