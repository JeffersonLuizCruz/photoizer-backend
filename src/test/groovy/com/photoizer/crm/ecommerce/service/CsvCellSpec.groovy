package com.photoizer.crm.ecommerce.service

import spock.lang.Specification

class CsvCellSpec extends Specification {

    def "neutraliza fórmulas e escapa aspas e quebras de linha"() {
        expect:
        CsvCell.texto("=1+1") == "\"'=1+1\""
        CsvCell.texto("+55 11 99999-9999") == "\"'+55 11 99999-9999\""
        CsvCell.texto("-10") == "\"'-10\""
        CsvCell.texto("@usuario") == "\"'@usuario\""
        CsvCell.texto("Ana\nSilva") == "\"Ana Silva\""
        CsvCell.texto("Ana \"Bia\"") == "\"Ana \"\"Bia\"\"\""
        CsvCell.texto(null) == ""
    }

    def "formata decimais com vírgula pt-BR"() {
        expect:
        CsvCell.decimal(new BigDecimal("150.00")) == "150,00"
        CsvCell.decimal(new BigDecimal("0")) == "0,00"
        CsvCell.decimal(null) == "0,00"
    }
}
