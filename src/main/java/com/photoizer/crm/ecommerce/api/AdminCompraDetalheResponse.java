package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.foto.api.FotoEnsaioResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record AdminCompraDetalheResponse(
    UUID id,
    UUID agendamentoId,
    BigDecimal valorTotal,
    String status,
    String urlComprovante,
    LocalDateTime dataPagamento,
    Integer quantidadeFotos,
    String metodoPagamento,
    List<FotoEnsaioResponse> fotos,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
