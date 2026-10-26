package com.photoizer.crm.ecommerce.api;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record SelecaoEnsaioResponse(
    UUID id,
    UUID agendamentoId,
    int numero,
    String status,
    String observacaoCliente,
    LocalDateTime enviadaEm,
    LocalDateTime iniciadaEm,
    LocalDateTime concluidaEm,
    LocalDateTime publicadaEm,
    LocalDateTime originaisBaixadosEm,
    long quantidadePacote,
    long quantidadeExtras,
    List<SelecaoEnsaioItemResponse> itens
) {}
