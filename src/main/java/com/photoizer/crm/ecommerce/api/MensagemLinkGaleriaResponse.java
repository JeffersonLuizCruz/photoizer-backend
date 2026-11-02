package com.photoizer.crm.ecommerce.api;

/**
 * Modelo de mensagem para envio do link da galeria ao cliente.
 *
 * <p>O texto contem o placeholder {@code {{link}}}, substituido pelo frontend
 * com a URL publica da galeria.
 */
public record MensagemLinkGaleriaResponse(String mensagem) {
}
