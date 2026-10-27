package com.photoizer.crm.foto.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Publicado quando o staff publica fotos de uma galeria.
 *
 * <p>O modulo foto nao conhece as regras de validade do link nem a data de
 * publicacao da galeria; os modulos interessados reagem ao evento.
 */
public record FotosGaleriaPublicadasEvent(
    UUID agendamentoId,
    LocalDateTime publicadoEm,
    int quantidade
) {
}
