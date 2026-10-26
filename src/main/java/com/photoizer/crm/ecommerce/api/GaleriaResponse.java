package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.foto.api.FotoEnsaioResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record GaleriaResponse(
    List<FotoEnsaioResponse> fotos,
    int pacoteQuantidadeFotos,
    BigDecimal valorUnitarioFotoExtra,
    String pacoteNome,
    String localEnsaio,
    String clienteNome,
    LocalDateTime dataHoraEnsaio,
    UUID capaFotoId,
    String capaVersao,
    String capaUrl,
    String modoGaleria,
    String faseGaleria,
    boolean requerEdicao,
    boolean podeSelecionar,
    boolean podeComprar,
    boolean podeBaixar,
    SelecaoEnsaioResponse selecaoAtual,
    Map<UUID, BigDecimal> valoresExtras
) {}
