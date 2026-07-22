package com.photoizer.crm.despesa.api;

import com.photoizer.crm.despesa.model.Despesa;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DespesaResponse(
    UUID id,
    String descricao,
    BigDecimal valor,
    String categoria,
    LocalDate data,
    String observacao
) {
    public static DespesaResponse of(Despesa d) {
        return new DespesaResponse(
            d.getId(), d.getDescricao(), d.getValor(),
            d.getCategoria(), d.getData(), d.getObservacao()
        );
    }
}
