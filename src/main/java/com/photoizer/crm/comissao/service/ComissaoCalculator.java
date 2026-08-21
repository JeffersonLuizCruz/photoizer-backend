package com.photoizer.crm.comissao.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calcula o valor da comissão de indicação.
 *
 * Pattern: Strategy — Extração da regra de cálculo para componente único.
 * O cálculo "valorReferencia × percentual / 100" estava duplicado em
 * IndicacaoService e FinanceiroService. Centralizar permite:
 * 1. Eliminar duplicação (DRY)
 * 2. Facilitar mudanças futuras (ex: diferentes fórmulas por tipo de comissão)
 * 3. Testes unitários isolados da regra de negócio
 */
@Component
public class ComissaoCalculator {

    private static final int ESCALA = 2;
    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    public BigDecimal calcular(BigDecimal valorReferencia, BigDecimal percentual) {
        if (valorReferencia == null || percentual == null) {
            return BigDecimal.ZERO;
        }
        return valorReferencia.multiply(percentual)
            .divide(CEM, ESCALA, RoundingMode.HALF_UP);
    }
}
