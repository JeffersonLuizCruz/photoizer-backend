package com.photoizer.crm.foto.model;

/**
 * Versao publica atual da foto.
 *
 * <p>Nao substitui {@link StatusFoto}: o status continua controlando o ciclo
 * de compra. Esta versao apenas indica qual arquivo o cliente deve receber.
 *
 * <p><strong>ORIGINAL</strong> significa o arquivo base enviado para a
 * galeria. Ele pode ja ter sido editado fora do sistema antes do upload;
 * portanto, ORIGINAL nao implica "foto nao editada" nem "RAW". No modo
 * ENTREGA_DIRETA, esse e o arquivo entregue ao cliente.
 */
public enum VersaoFoto {
    ORIGINAL,
    EDITADA
}
