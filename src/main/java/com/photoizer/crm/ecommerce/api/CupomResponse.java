package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.model.Cupom;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record CupomResponse(
    UUID id,
    String codigo,
    String descricao,
    String tipoDesconto,
    BigDecimal valorDesconto,
    BigDecimal valorMinimoPedido,
    Integer usoLimite,
    Integer usosAtuais,
    LocalDate dataValidade,
    boolean ativo,
    boolean usoUnico,
    LocalDateTime createdAt
) {
    public static CupomResponse of(Cupom c) {
        return new CupomResponse(
            c.getId(), c.getCodigo(), c.getDescricao(), c.getTipoDesconto(),
            c.getValorDesconto(), c.getValorMinimoPedido(), c.getUsoLimite(),
            c.getUsosAtuais(), c.getDataValidade(), c.getAtivo(), c.getUsoUnico(),
            c.getCreatedAt()
        );
    }
}
