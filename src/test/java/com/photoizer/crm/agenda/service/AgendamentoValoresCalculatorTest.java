package com.photoizer.crm.agenda.service;

import com.photoizer.crm.shared.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgendamentoValoresCalculatorTest {

    private final AgendamentoValoresCalculator calculator = new AgendamentoValoresCalculator();

    @Test
    @DisplayName("aplica entrada mínima de 1 centavo quando o percentual arredonda para zero")
    void aplicaEntradaMinimaQuandoPercentualArredondaParaZero() {
        var valores = calculator.calcularValoresNovo(
            new BigDecimal("0.01"), BigDecimal.ZERO, new BigDecimal("30.00"));

        assertThat(valores.valorTotal()).isEqualByComparingTo("0.01");
        assertThat(valores.valorEntradaExigido()).isEqualByComparingTo("0.01");
        assertThat(valores.valorRestante()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("aplica entrada mínima também no recálculo da proposta")
    void aplicaEntradaMinimaNoRecalculo() {
        var valores = calculator.calcularValoresAtualizacao(
            new BigDecimal("0.01"), BigDecimal.ZERO, new BigDecimal("30.00"),
            BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(valores.valorEntradaExigido()).isEqualByComparingTo("0.01");
    }

    @Test
    @DisplayName("mantém o cálculo normal de entrada")
    void mantemCalculoNormal() {
        var valores = calculator.calcularValoresNovo(
            new BigDecimal("2000.00"), new BigDecimal("100.00"), new BigDecimal("30.00"));

        assertThat(valores.valorTotal()).isEqualByComparingTo("2100.00");
        assertThat(valores.valorEntradaExigido()).isEqualByComparingTo("630.00");
    }

    @Test
    @DisplayName("não deixa a entrada ultrapassar o valor total")
    void naoDeixaEntradaUltrapassarTotal() {
        var valores = calculator.calcularValoresNovo(
            new BigDecimal("0.01"), BigDecimal.ZERO, new BigDecimal("100.00"));

        assertThat(valores.valorEntradaExigido()).isEqualByComparingTo("0.01");
    }

    @Test
    @DisplayName("rejeita valor total não positivo")
    void rejeitaValorTotalNaoPositivo() {
        assertThatThrownBy(() -> calculator.calcularValoresNovo(
            BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("30.00")))
            .isInstanceOf(BadRequestException.class);
    }
}
