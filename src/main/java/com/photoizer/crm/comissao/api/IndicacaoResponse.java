package com.photoizer.crm.comissao.api;

import com.photoizer.crm.comissao.model.Indicacao;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record IndicacaoResponse(
    UUID id,
    UUID indicadorId,
    String indicadorNome,
    String indicadorTelefone,
    String origem,
    BigDecimal percentual,
    BigDecimal valorReferencia,
    BigDecimal valorComissao,
    String status,
    LocalDateTime dataPagamento,
    String clienteNome,
    String pacoteNome,
    BigDecimal valorTotalFinal,
    BigDecimal valorExtras,
    LocalDateTime dataHoraEnsaio
) {
    public static IndicacaoResponse of(Indicacao i, String clienteNome, String pacoteNome,
                                        BigDecimal valorTotalFinal, BigDecimal valorExtras,
                                        LocalDateTime dataHoraEnsaio) {
        return new IndicacaoResponse(
            i.getId(), i.getIndicadorId(),
            i.getIndicadorNome(), i.getIndicadorTelefone(), i.getOrigem(),
            i.getPercentual(), i.getValorReferencia(), i.getValorComissao(),
            i.getStatus(), i.getDataPagamento(),
            clienteNome, pacoteNome,
            valorTotalFinal, valorExtras, dataHoraEnsaio
        );
    }
}
