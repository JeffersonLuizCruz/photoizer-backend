package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.model.EtapaGaleria;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Item da fila operacional da Central de Galerias.
 *
 * <p>`etapa` e derivada; `proximaAcao`/`acaoUrl` ja apontam para a tela onde o
 * staff resolve a pendencia.
 */
public record GaleriaPipelineItemResponse(
    UUID agendamentoId,
    String clienteNome,
    String pacoteNome,
    String localEnsaio,
    LocalDateTime dataHoraEnsaio,
    LocalDate prazoEntrega,
    EtapaGaleria etapa,
    String etapaLabel,
    String proximaAcao,
    String acaoUrl,
    String linkGaleria,
    int totalFotos,
    int fotosPublicadas,
    int fotosSelecionadas,
    int fotosBaixadas,
    int extrasPendentesEdicao,
    int comprasPendentes,
    BigDecimal valorExtras,
    String selecaoStatus,
    Integer selecaoNumero,
    LocalDateTime selecaoEnviadaEm,
    LocalDateTime publicadaEm,
    long diasNaEtapa,
    boolean atrasada,
    boolean tokenExpirado,
    LocalDateTime tokenExpiracao
) {
}
