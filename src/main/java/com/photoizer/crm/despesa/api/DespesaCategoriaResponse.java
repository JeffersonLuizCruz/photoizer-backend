package com.photoizer.crm.despesa.api;

import com.photoizer.crm.despesa.model.DespesaCategoria;

import java.util.UUID;

public record DespesaCategoriaResponse(
    UUID id,
    String nome,
    String cor,
    Boolean ativo,
    Integer ordem,
    long qtdDespesas
) {
    public static DespesaCategoriaResponse of(DespesaCategoria c, long qtdDespesas) {
        return new DespesaCategoriaResponse(
            c.getId(), c.getNome(), c.getCor(), c.getAtivo(), c.getOrdem(), qtdDespesas
        );
    }
}
