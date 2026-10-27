package com.photoizer.crm.ecommerce.api;

import java.time.LocalDateTime;
import java.util.UUID;

public record GaleriaEnsaioResponse(
    UUID agendamentoId,
    String modo,
    String fase,
    UUID selecaoAtualId,
    UUID capaFotoId,
    String capaVersao,
    String tituloHero,
    boolean requerEdicao,
    boolean podeAlterarModo,
    boolean arquivada,
    LocalDateTime arquivadaEm,
    long bytesLiberados,
    LocalDateTime publicadaEm
) {}
