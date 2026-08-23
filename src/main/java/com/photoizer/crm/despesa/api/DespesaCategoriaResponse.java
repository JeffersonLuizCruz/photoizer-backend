package com.photoizer.crm.despesa.api;

import java.util.UUID;

public record DespesaCategoriaResponse(
    UUID id,
    String nome,
    String cor,
    Boolean ativo,
    Integer ordem,
    long qtdDespesas
) {}
