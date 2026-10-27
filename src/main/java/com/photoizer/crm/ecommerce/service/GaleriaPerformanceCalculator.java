package com.photoizer.crm.ecommerce.service;

import com.photoizer.crm.ecommerce.api.GaleriaPerformanceResponse;
import com.photoizer.crm.ecommerce.api.GaleriaPerformanceResponse.SerieMensalResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Calcula os indicadores da Central de Galerias a partir de fatos ja coletados
 * do banco. Regra pura, sem dependencia de Spring/repositorios, para ser testada
 * isoladamente.
 */
public final class GaleriaPerformanceCalculator {

    private static final DateTimeFormatter MES_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private GaleriaPerformanceCalculator() {
    }

    public record DadosGaleriaPerformance(
        LocalDateTime dataEnsaio,
        boolean publicada,
        boolean comercializada,
        boolean atrasada,
        int fotosPagas,
        int comprasPagas,
        BigDecimal receitaExtras,
        LocalDateTime publicadaEm,
        LocalDateTime selecaoEnviadaEm,
        LocalDateTime selecaoPublicadaEm
    ) {
    }

    public static GaleriaPerformanceResponse calcular(
            LocalDate referencia, int meses, List<DadosGaleriaPerformance> dados) {
        var publicadas = 0;
        var comSelecao = 0;
        var comercializadas = 0;
        var comExtras = 0;
        var atrasadas = 0;
        var totalFotosExtras = 0;
        var totalComprasPagas = 0;
        var receitaExtras = BigDecimal.ZERO;
        var amostrasPublicacao = new ArrayList<Long>();
        var amostrasEdicao = new ArrayList<Long>();
        var amostrasEntrega = new ArrayList<Long>();

        for (var item : dados) {
            if (item.publicada()) {
                publicadas++;
            }
            if (item.selecaoEnviadaEm() != null) {
                comSelecao++;
            }
            if (item.comercializada()) {
                comercializadas++;
            }
            if (item.atrasada()) {
                atrasadas++;
            }
            if (item.comprasPagas() > 0) {
                comExtras++;
            }
            totalFotosExtras += item.fotosPagas();
            totalComprasPagas += item.comprasPagas();
            receitaExtras = receitaExtras.add(item.receitaExtras() != null ? item.receitaExtras() : BigDecimal.ZERO);

            if (item.publicadaEm() != null && item.dataEnsaio() != null) {
                amostrasPublicacao.add(dias(item.dataEnsaio(), item.publicadaEm()));
            }
            if (item.selecaoEnviadaEm() != null && item.selecaoPublicadaEm() != null) {
                amostrasEdicao.add(dias(item.selecaoEnviadaEm(), item.selecaoPublicadaEm()));
                if (item.dataEnsaio() != null) {
                    amostrasEntrega.add(dias(item.dataEnsaio(), item.selecaoPublicadaEm()));
                }
            }
        }

        var ticketMedio = totalComprasPagas > 0
            ? receitaExtras.divide(BigDecimal.valueOf(totalComprasPagas), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        var extrasPorGaleria = comercializadas > 0
            ? BigDecimal.valueOf(totalFotosExtras).divide(BigDecimal.valueOf(comercializadas), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        var conversaoExtras = comercializadas > 0
            ? arredondar((double) comExtras / comercializadas * 100.0)
            : 0.0;

        return new GaleriaPerformanceResponse(
            meses,
            dados.size(),
            publicadas,
            comSelecao,
            comercializadas,
            comExtras,
            atrasadas,
            media(amostrasPublicacao),
            media(amostrasEdicao),
            media(amostrasEntrega),
            receitaExtras,
            ticketMedio,
            extrasPorGaleria,
            conversaoExtras,
            totalFotosExtras,
            totalComprasPagas,
            serieMensal(referencia, meses, dados));
    }

    private static List<SerieMensalResponse> serieMensal(
            LocalDate referencia, int meses, List<DadosGaleriaPerformance> dados) {
        var porMes = new TreeMap<YearMonth, List<DadosGaleriaPerformance>>();
        for (var item : dados) {
            if (item.dataEnsaio() == null) {
                continue;
            }
            porMes.computeIfAbsent(YearMonth.from(item.dataEnsaio()), chave -> new ArrayList<>()).add(item);
        }

        var mesAtual = YearMonth.from(referencia);
        var serie = new ArrayList<SerieMensalResponse>(meses);
        for (int i = meses - 1; i >= 0; i--) {
            var mes = mesAtual.minusMonths(i);
            var itensDoMes = porMes.getOrDefault(mes, List.of());
            var publicadas = itensDoMes.stream().filter(DadosGaleriaPerformance::publicada).count();
            var comExtras = itensDoMes.stream().filter(item -> item.comprasPagas() > 0).count();
            var receita = itensDoMes.stream()
                .map(DadosGaleriaPerformance::receitaExtras)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            var amostras = itensDoMes.stream()
                .filter(item -> item.publicadaEm() != null && item.dataEnsaio() != null)
                .map(item -> dias(item.dataEnsaio(), item.publicadaEm()))
                .toList();
            serie.add(new SerieMensalResponse(
                mes.format(MES_FORMATTER),
                itensDoMes.size(),
                (int) publicadas,
                (int) comExtras,
                receita,
                media(amostras)));
        }
        return serie;
    }

    private static Integer media(List<Long> amostras) {
        if (amostras.isEmpty()) {
            return null;
        }
        return (int) Math.round(amostras.stream().mapToLong(Long::longValue).average().orElse(0));
    }

    private static long dias(LocalDateTime inicio, LocalDateTime fim) {
        return Math.max(0, ChronoUnit.DAYS.between(inicio.toLocalDate(), fim.toLocalDate()));
    }

    private static double arredondar(double valor) {
        return BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
