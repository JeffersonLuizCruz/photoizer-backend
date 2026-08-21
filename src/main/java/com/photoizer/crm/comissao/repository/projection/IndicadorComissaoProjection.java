package com.photoizer.crm.comissao.repository.projection;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Projeção leve para resultados de queries agregadas de comissões por indicador.
 * Elimina N+1 queries ao substituir o loop de soma em memória por GROUP BY no banco.
 */
public interface IndicadorComissaoProjection {
    String getTelefone();
    String getNome();
    UUID getIndicadorId();
    BigDecimal getTotalPendente();
    BigDecimal getTotalPago();
    BigDecimal getTotalCancelado();
    int getTotalIndicacoes();
}
