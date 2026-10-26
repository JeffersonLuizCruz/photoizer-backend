package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.foto.api.FotoEnsaioResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record SelecaoEnsaioItemResponse(
    UUID fotoId,
    int numeroGaleria,
    String nomeArquivo,
    String tipo,
    int ordem,
    BigDecimal valorUnitario,
    boolean editada,
    LocalDateTime editadaEm,
    FotoEnsaioResponse foto
) {}
