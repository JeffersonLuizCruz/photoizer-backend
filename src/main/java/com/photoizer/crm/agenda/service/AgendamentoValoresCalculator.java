package com.photoizer.crm.agenda.service;

import com.photoizer.crm.shared.model.TipoRepasse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class AgendamentoValoresCalculator {

    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    public record ValoresAgendamento(
            BigDecimal valorTotal,
            BigDecimal valorEntradaExigido,
            BigDecimal valorEntradaPago,
            BigDecimal valorRestante,
            BigDecimal valorExtras,
            BigDecimal valorTotalFinal,
            BigDecimal taxaDeslocamento) {
    }

    public ValoresAgendamento calcularValoresNovo(BigDecimal valorBasePacote, BigDecimal taxaDeslocamento,
                                                  BigDecimal percentualEntrada) {
        var valorTotal = valorBasePacote.add(taxaDeslocamento);
        var valorEntradaExigido = calcularEntradaExigida(valorTotal, percentualEntrada);
        var valorEntradaPago = valorEntradaExigido;
        var valorExtras = BigDecimal.ZERO;
        return new ValoresAgendamento(valorTotal, valorEntradaExigido, valorEntradaPago,
            valorTotal.subtract(valorEntradaPago), valorExtras, valorTotal.add(valorExtras), taxaDeslocamento);
    }

    public ValoresAgendamento calcularValoresAtualizacao(BigDecimal valorBasePacote, BigDecimal taxaDeslocamento,
                                                         BigDecimal percentualEntrada,
                                                         BigDecimal valorEntradaPago, BigDecimal valorExtras) {
        var valorTotal = valorBasePacote.add(taxaDeslocamento);
        var valorEntradaExigido = calcularEntradaExigida(valorTotal, percentualEntrada);
        var valorRestante = valorTotal.subtract(valorEntradaPago);
        return new ValoresAgendamento(valorTotal, valorEntradaExigido, valorEntradaPago,
            valorRestante, valorExtras, valorTotal.add(valorExtras), taxaDeslocamento);
    }

    public BigDecimal valorRepasseEfetivo(BigDecimal base, TipoRepasse tipo, BigDecimal valorRepassar,
                                          BigDecimal percentual) {
        if (tipo == TipoRepasse.PERCENTUAL) {
            var baseEfetiva = base != null ? base : BigDecimal.ZERO;
            var pct = percentual != null ? percentual : BigDecimal.ZERO;
            return baseEfetiva.multiply(pct).divide(CEM, 2, RoundingMode.HALF_UP);
        }
        return valorRepassar != null ? valorRepassar : BigDecimal.ZERO;
    }

    public BigDecimal calcularValorRepasse(BigDecimal base, TipoRepasse tipo, BigDecimal valorRepassar,
                                           BigDecimal percentual) {
        if (tipo == TipoRepasse.PERCENTUAL) {
            if (percentual == null) {
                throw new IllegalArgumentException("Percentual é obrigatório quando o tipo é PERCENTUAL");
            }
            return valorRepasseEfetivo(base, tipo, valorRepassar, percentual);
        }
        return valorRepassar != null ? valorRepassar : BigDecimal.ZERO;
    }

    private BigDecimal calcularEntradaExigida(BigDecimal valorTotal, BigDecimal percentualEntrada) {
        var fator = percentualEntrada.divide(CEM, 4, RoundingMode.HALF_UP);
        return valorTotal.multiply(fator).setScale(2, RoundingMode.HALF_UP);
    }

}