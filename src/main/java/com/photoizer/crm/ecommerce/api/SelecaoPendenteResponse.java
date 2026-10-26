package com.photoizer.crm.ecommerce.api;

import java.time.LocalDateTime;
import java.util.UUID;

public record SelecaoPendenteResponse(
    UUID agendamentoId,
    UUID selecaoId,
    int numero,
    String status,
    String clienteNome,
    String pacoteNome,
    long quantidadePacote,
    long quantidadeExtras,
    LocalDateTime enviadaEm,
    long diasAguardando
) {}
