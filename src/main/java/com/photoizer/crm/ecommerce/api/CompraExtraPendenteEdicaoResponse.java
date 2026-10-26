package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.foto.api.FotoEnsaioResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Compra paga que possui fotos extras aguardando a edição sob demanda.
 * Usada na fila do workspace da galeria (ADMIN/FOTOGRAFO/EDITOR).
 */
public record CompraExtraPendenteEdicaoResponse(
    UUID compraId,
    UUID agendamentoId,
    String clienteNome,
    BigDecimal valorTotal,
    LocalDateTime dataPagamento,
    List<FotoEnsaioResponse> fotos
) {}
