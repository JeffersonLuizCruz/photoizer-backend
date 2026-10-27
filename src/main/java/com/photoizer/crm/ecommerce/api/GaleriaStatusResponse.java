package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.model.EtapaGaleria;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Status resumido da galeria para o cliente, disponivel mesmo antes da
 * publicacao das fotos. Nao expoe SLA interno, pagamentos ou dados do staff.
 */
public record GaleriaStatusResponse(
    EtapaGaleria etapa,
    String etapaLabel,
    String mensagem,
    LocalDate previsaoEntrega,
    int totalFotos,
    int fotosPublicadas,
    String selecaoStatus,
    LocalDateTime selecaoEnviadaEm,
    LocalDateTime tokenExpiracao
) {
}
