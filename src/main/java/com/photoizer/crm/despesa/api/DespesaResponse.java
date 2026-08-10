package com.photoizer.crm.despesa.api;

import com.photoizer.crm.despesa.model.Despesa;
import com.photoizer.crm.despesa.model.RecorrenciaDespesa;
import com.photoizer.crm.despesa.model.StatusDespesa;
import com.photoizer.crm.shared.model.FormaPagamento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record DespesaResponse(
    UUID id,
    String descricao,
    BigDecimal valor,
    UUID categoriaId,
    String categoria,
    String cor,
    LocalDate data,
    FormaPagamento formaPagamento,
    StatusDespesa status,
    RecorrenciaDespesa recorrencia,
    LocalDate dataProximaGeracao,
    UUID geradaDeId,
    UUID agendamentoId,
    LocalDateTime dataPagamento,
    String urlComprovante,
    String observacao
) {
    public static DespesaResponse of(Despesa d) {
        var cat = d.getCategoriaRef();
        return new DespesaResponse(
            d.getId(),
            d.getDescricao(),
            d.getValor(),
            cat != null ? cat.getId() : null,
            cat != null ? cat.getNome() : d.getCategoria(),
            cat != null ? cat.getCor() : null,
            d.getData(),
            d.getFormaPagamento(),
            d.getStatus(),
            d.getRecorrencia(),
            d.getDataProximaGeracao(),
            d.getGeradaDeId(),
            d.getAgendamentoId(),
            d.getDataPagamento(),
            d.getUrlComprovante(),
            d.getObservacao()
        );
    }
}
