package com.photoizer.crm.ecommerce.api;

import java.util.UUID;

public record GaleriaEnsaioResponse(
    UUID agendamentoId,
    String modo,
    String fase,
    UUID selecaoAtualId,
    UUID capaFotoId,
    String capaVersao,
    boolean requerEdicao,
    boolean podeAlterarModo
) {}
