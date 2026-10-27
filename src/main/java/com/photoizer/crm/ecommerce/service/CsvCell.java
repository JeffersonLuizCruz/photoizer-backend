package com.photoizer.crm.ecommerce.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Formatação segura de células CSV.
 *
 * <p>Neutraliza fórmulas do Excel (=, +, -, @, tab), remove quebras de linha e
 * escapa aspas. Valores decimais usam vírgula para o locale pt-BR.
 */
final class CsvCell {

    private CsvCell() {
    }

    static String texto(String valor) {
        if (valor == null) {
            return "";
        }
        var limpo = valor.replace('\r', ' ').replace('\n', ' ').trim();
        if (!limpo.isEmpty() && "=+-@\t".indexOf(limpo.charAt(0)) >= 0) {
            limpo = "'" + limpo;
        }
        return "\"" + limpo.replace("\"", "\"\"") + "\"";
    }

    static String decimal(BigDecimal valor) {
        if (valor == null) {
            return "0,00";
        }
        return valor.setScale(2, RoundingMode.HALF_UP)
            .toPlainString()
            .replace('.', ',');
    }
}
