package com.photoizer.crm.financeiro.api;

import com.photoizer.crm.financeiro.model.TipoServico;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InadimplenciaRelatorioResponse(
    BigDecimal totalEmAberto,
    List<Item> itens
) {
    public record Item(
        UUID receitaId,
        String clienteNome,
        TipoServico tipoServico,
        String descricao,
        BigDecimal valorFinal,
        BigDecimal valorRecebido,
        BigDecimal valorEmAberto,
        LocalDate dataPrevisaoRecebimento,
        long diasAtraso
    ) {}
}
