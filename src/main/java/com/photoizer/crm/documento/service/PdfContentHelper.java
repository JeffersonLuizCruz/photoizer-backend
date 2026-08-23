package com.photoizer.crm.documento.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * PATTERN: DRY (Don't Repeat Yourself)
 *
 * Centraliza utilitários de formatação para estratégias de PDF.
 * Antes: formatarValor() duplicado em ContratoPdfStrategy e ReciboPdfStrategy.
 * Agora: ponto único de manutenção.
 */
public final class PdfContentHelper {

    private PdfContentHelper() {
    }

    /**
     * Formata valorBigDecimal para exibição em PDF (padrão brasileiro: 1.234,56).
     * Retorna "0,00" para null.
     */
    public static String formatarValor(BigDecimal valor) {
        if (valor == null) return "0,00";
        return valor.setScale(2, RoundingMode.HALF_UP)
            .toPlainString().replace(".", ",");
    }
}
