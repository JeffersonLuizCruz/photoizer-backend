package com.photoizer.crm.agenda.service;

import com.photoizer.crm.shared.exception.BadRequestException;
import com.photoizer.crm.shared.model.TipoRepasse;
import com.photoizer.crm.shared.util.EntradaUtils;
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
            BigDecimal taxaDeslocamento,
            BigDecimal percentualEntrada) {
    }

    public ValoresAgendamento calcularValoresNovo(BigDecimal valorBasePacote, BigDecimal taxaDeslocamento,
                                                  BigDecimal percentualEntrada) {
        var valorTotal = valorBasePacote.add(taxaDeslocamento);
        var valorEntradaExigido = EntradaUtils.calcularEntradaExigida(valorTotal, percentualEntrada);
        var valorEntradaPago = valorEntradaExigido;
        var valorExtras = BigDecimal.ZERO;
        return new ValoresAgendamento(valorTotal, valorEntradaExigido, valorEntradaPago,
            valorTotal.subtract(valorEntradaPago), valorExtras, valorTotal.add(valorExtras),
            taxaDeslocamento, percentualEntrada);
    }

    /**
     * Calcula os valores de uma proposta nova. Quando o staff informa uma entrada
     * já paga, ela substitui o percentual configurado e passa a ser a própria
     * entrada exigida (o cliente não precisa pagar reserva novamente).
     */
    public ValoresAgendamento calcularValoresProposta(BigDecimal valorBasePacote, BigDecimal taxaDeslocamento,
                                                      BigDecimal percentualEntrada,
                                                      BigDecimal valorEntradaPagoInformado) {
        var valorTotal = valorBasePacote.add(taxaDeslocamento);
        if (entradaInformada(valorEntradaPagoInformado)) {
            return comEntradaPaga(valorTotal, BigDecimal.ZERO, taxaDeslocamento, valorEntradaPagoInformado);
        }

        var valorEntradaExigido = EntradaUtils.calcularEntradaExigida(valorTotal, percentualEntrada);
        return new ValoresAgendamento(valorTotal, valorEntradaExigido, BigDecimal.ZERO,
            valorTotal, BigDecimal.ZERO, valorTotal, taxaDeslocamento, percentualEntrada);
    }

    public ValoresAgendamento calcularValoresAtualizacao(BigDecimal valorBasePacote, BigDecimal taxaDeslocamento,
                                                         BigDecimal percentualEntrada,
                                                         BigDecimal valorEntradaPago, BigDecimal valorExtras) {
        return calcularValoresAtualizacao(valorBasePacote, taxaDeslocamento, percentualEntrada,
            valorEntradaPago, valorExtras, null);
    }

    public ValoresAgendamento calcularValoresAtualizacao(BigDecimal valorBasePacote, BigDecimal taxaDeslocamento,
                                                         BigDecimal percentualEntrada,
                                                         BigDecimal valorEntradaPago, BigDecimal valorExtras,
                                                         BigDecimal valorEntradaPagoInformado) {
        var valorTotal = valorBasePacote.add(taxaDeslocamento);
        if (entradaInformada(valorEntradaPagoInformado)) {
            return comEntradaPaga(valorTotal, valorExtras, taxaDeslocamento, valorEntradaPagoInformado);
        }

        var valorEntradaExigido = EntradaUtils.calcularEntradaExigida(valorTotal, percentualEntrada);
        var valorEntradaPagoEfetivo = valorEntradaPago != null ? valorEntradaPago : BigDecimal.ZERO;
        var valorExtrasEfetivo = valorExtras != null ? valorExtras : BigDecimal.ZERO;
        var valorTotalFinal = valorTotal.add(valorExtrasEfetivo);
        var valorRestante = valorTotalFinal.subtract(valorEntradaPagoEfetivo).max(BigDecimal.ZERO);
        return new ValoresAgendamento(valorTotal, valorEntradaExigido, valorEntradaPagoEfetivo,
            valorRestante, valorExtrasEfetivo, valorTotalFinal, taxaDeslocamento, percentualEntrada);
    }

    private ValoresAgendamento comEntradaPaga(BigDecimal valorTotal, BigDecimal valorExtras,
                                              BigDecimal taxaDeslocamento, BigDecimal entradaInformada) {
        if (valorTotal.signum() <= 0) {
            throw new BadRequestException("O valor total do agendamento deve ser maior que zero");
        }

        var valorEntradaPago = entradaInformada.setScale(2, RoundingMode.HALF_UP);
        if (valorEntradaPago.signum() <= 0) {
            throw new BadRequestException("O valor de entrada já pago deve ser maior que zero");
        }
        if (valorEntradaPago.compareTo(valorTotal) > 0) {
            throw new BadRequestException("O valor de entrada já pago não pode ser maior que o valor total");
        }

        var valorTotalFinal = valorTotal.add(valorExtras);
        var percentualEntrada = valorEntradaPago.multiply(CEM)
            .divide(valorTotal, 2, RoundingMode.HALF_UP);
        return new ValoresAgendamento(valorTotal, valorEntradaPago, valorEntradaPago,
            valorTotalFinal.subtract(valorEntradaPago).max(BigDecimal.ZERO), valorExtras,
            valorTotalFinal, taxaDeslocamento, percentualEntrada);
    }

    private boolean entradaInformada(BigDecimal valorEntradaPagoInformado) {
        return valorEntradaPagoInformado != null && valorEntradaPagoInformado.signum() != 0;
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
                throw new BadRequestException("Percentual é obrigatório quando o tipo é PERCENTUAL");
            }
            return valorRepasseEfetivo(base, tipo, valorRepassar, percentual);
        }
        return valorRepassar != null ? valorRepassar : BigDecimal.ZERO;
    }

}
