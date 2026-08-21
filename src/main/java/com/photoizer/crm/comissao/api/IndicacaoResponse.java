package com.photoizer.crm.comissao.api;

import com.photoizer.crm.comissao.model.Indicacao;
import com.photoizer.crm.comissao.model.OrigemIndicacao;
import com.photoizer.crm.comissao.model.StatusIndicacao;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record IndicacaoResponse(
    UUID id,
    UUID indicadorId,
    String indicadorNome,
    String indicadorTelefone,
    OrigemIndicacao origem,
    BigDecimal percentual,
    BigDecimal valorReferencia,
    BigDecimal valorComissao,
    StatusIndicacao status,
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
