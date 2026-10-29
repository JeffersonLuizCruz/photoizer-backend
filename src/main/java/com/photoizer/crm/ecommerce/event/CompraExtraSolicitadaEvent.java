package com.photoizer.crm.ecommerce.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Evento publicado quando o cliente finaliza o checkout de fotos extras.
 * Enriquecido com os dados que o módulo de notificação precisa, evitando que
 * ele consulte repositórios de outros módulos.
 */
public record CompraExtraSolicitadaEvent(
    UUID agendamentoId,
    UUID compraId,
    String clienteNome,
    UUID fotografoId,
    int quantidadeFotos,
    BigDecimal valorTotal
) {
}
