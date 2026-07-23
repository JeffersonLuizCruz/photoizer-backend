package com.photoizer.crm.ecommerce.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PedidoRequest(
    @NotNull UUID clienteId,
    @NotNull UUID pacoteId,
    UUID agendamentoId,
    List<UUID> fotosSelecionadasIds,
    List<UUID> fotosExtrasIds,
    @NotNull @PositiveOrZero BigDecimal taxaEntrega,
    String opcaoEntrega,
    String formaPagamento,
    String codigoCupom
) {}
