package com.photoizer.crm.ecommerce.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Evento publicado na primeira vez que o comprovante de uma compra de extras é
 * enviado. Reenvios do mesmo comprovante não publicam novamente.
 */
public record ComprovanteCompraEnviadoEvent(
    UUID agendamentoId,
    UUID compraId,
    String clienteNome,
    UUID fotografoId,
    int quantidadeFotos,
    BigDecimal valorTotal
) {
}
