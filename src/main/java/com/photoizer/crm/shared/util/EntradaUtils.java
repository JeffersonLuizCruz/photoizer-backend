package com.photoizer.crm.shared.util;

import com.photoizer.crm.shared.exception.BadRequestException;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class EntradaUtils {

    private static final BigDecimal CEM = BigDecimal.valueOf(100);
    private static final BigDecimal ENTRADA_MINIMA = new BigDecimal("0.01");

    private EntradaUtils() {
    }

    public static BigDecimal calcularEntradaExigida(BigDecimal valorTotal, BigDecimal percentualEntrada) {
        if (valorTotal == null || valorTotal.signum() <= 0) {
            throw new BadRequestException("O valor total do agendamento deve ser maior que zero");
        }

        var percentual = percentualEntrada != null ? percentualEntrada : BigDecimal.ZERO;
        if (percentual.signum() < 0) {
            throw new BadRequestException("O percentual de entrada não pode ser negativo");
        }

        var fator = percentual.divide(CEM, 4, RoundingMode.HALF_UP);
        var entrada = valorTotal.multiply(fator).setScale(2, RoundingMode.HALF_UP);

        return entrada.max(ENTRADA_MINIMA).min(valorTotal);
    }
}
