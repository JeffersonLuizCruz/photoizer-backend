package com.photoizer.crm.foto.model;

/**
 * Versao publica atual da foto.
 *
 * <p>Nao substitui {@link StatusFoto}: o status continua controlando o ciclo
 * de compra. Esta versao apenas indica se o cliente deve receber o RAW ou o
 * arquivo editado.
 */
public enum VersaoFoto {
    ORIGINAL,
    EDITADA
}
