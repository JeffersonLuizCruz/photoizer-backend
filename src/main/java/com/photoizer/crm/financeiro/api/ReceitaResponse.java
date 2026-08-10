package com.photoizer.crm.financeiro.api;

import com.photoizer.crm.financeiro.model.Receita;
import com.photoizer.crm.financeiro.model.StatusReceita;
import com.photoizer.crm.financeiro.model.TipoServico;
import com.photoizer.crm.shared.model.FormaPagamento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ReceitaResponse(
    UUID id,
    UUID agendamentoId,
    UUID clienteId,
    String clienteNome,
    TipoServico tipoServico,
    String descricao,
    BigDecimal valorBruto,
    BigDecimal valorComissao,
    BigDecimal valorFinal,
    StatusReceita status,
    BigDecimal valorRecebido,
    LocalDate dataPrevisaoRecebimento,
    LocalDateTime dataRecebimentoReal,
    FormaPagamento formaPagamento,
    String observacoes,
    LocalDateTime createdAt
) {
    public static ReceitaResponse of(Receita r) {
        return new ReceitaResponse(
            r.getId(), r.getAgendamentoId(), r.getClienteId(), r.getClienteNome(),
            r.getTipoServico(), r.getDescricao(), r.getValorBruto(), r.getValorComissao(),
            r.getValorFinal(), r.getStatus(), r.getValorRecebido(),
            r.getDataPrevisaoRecebimento(), r.getDataRecebimentoReal(),
            r.getFormaPagamento(), r.getObservacoes(), r.getCreatedAt()
        );
    }
}
