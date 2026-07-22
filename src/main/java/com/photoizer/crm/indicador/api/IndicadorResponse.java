package com.photoizer.crm.indicador.api;

import com.photoizer.crm.indicador.model.Indicador;

import java.math.BigDecimal;
import java.util.UUID;

public record IndicadorResponse(
    UUID id,
    String nome,
    String telefone,
    String observacoes,
    BigDecimal totalPendente,
    BigDecimal totalPago,
    long totalIndicacoes
) {
    public static IndicadorResponse of(Indicador i, BigDecimal totalPendente, BigDecimal totalPago, long totalIndicacoes) {
        return new IndicadorResponse(
            i.getId(), i.getNome(), i.getTelefone(), i.getObservacoes(),
            totalPendente, totalPago, totalIndicacoes
        );
    }
}
