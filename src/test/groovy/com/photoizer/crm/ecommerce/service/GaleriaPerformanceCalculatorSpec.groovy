package com.photoizer.crm.ecommerce.service

import com.photoizer.crm.ecommerce.service.GaleriaPerformanceCalculator.DadosGaleriaPerformance
import spock.lang.Specification

import java.time.LocalDate
import java.time.LocalDateTime

class GaleriaPerformanceCalculatorSpec extends Specification {

    def "calcula funil, SLAs e série mensal a partir dos fatos das galerias"() {
        given: "uma galeria entregue em setembro e uma ainda em preparação em outubro"
        def referencia = LocalDate.of(2026, 10, 27)
        def dados = [
            new DadosGaleriaPerformance(
                LocalDateTime.of(2026, 9, 1, 10, 0),
                true,
                true,
                false,
                3,
                2,
                new BigDecimal("150.00"),
                LocalDateTime.of(2026, 9, 6, 10, 0),
                LocalDateTime.of(2026, 9, 8, 10, 0),
                LocalDateTime.of(2026, 9, 12, 10, 0)),
            new DadosGaleriaPerformance(
                LocalDateTime.of(2026, 10, 5, 10, 0),
                false,
                false,
                true,
                0,
                0,
                BigDecimal.ZERO,
                null,
                null,
                null)
        ]

        when: "os indicadores são calculados para os últimos 2 meses"
        def resultado = GaleriaPerformanceCalculator.calcular(referencia, 2, dados)

        then: "o funil reflete as duas galerias"
        resultado.totalGalerias() == 2
        resultado.publicadas() == 1
        resultado.comSelecao() == 1
        resultado.comercializadas() == 1
        resultado.comExtras() == 1
        resultado.atrasadas() == 1

        and: "os SLAs médios usam as datas corretas"
        resultado.tempoMedioPublicacaoDias() == 5
        resultado.tempoMedioEdicaoDias() == 4
        resultado.tempoMedioEntregaDias() == 11

        and: "os extras são consolidados"
        resultado.receitaExtras() == new BigDecimal("150.00")
        resultado.ticketMedioExtras() == new BigDecimal("75.00")
        resultado.extrasPorGaleria() == new BigDecimal("3.00")
        resultado.conversaoExtras() == 100.0
        resultado.totalFotosExtras() == 3
        resultado.totalComprasPagas() == 2

        and: "a série mensal cobre setembro e outubro"
        resultado.serieMensal().size() == 2
        resultado.serieMensal()[0].mes() == "2026-09"
        resultado.serieMensal()[0].galerias() == 1
        resultado.serieMensal()[0].publicadas() == 1
        resultado.serieMensal()[0].comExtras() == 1
        resultado.serieMensal()[0].receitaExtras() == new BigDecimal("150.00")
        resultado.serieMensal()[0].tempoMedioPublicacaoDias() == 5
        resultado.serieMensal()[1].mes() == "2026-10"
        resultado.serieMensal()[1].galerias() == 1
        resultado.serieMensal()[1].tempoMedioPublicacaoDias() == null
    }

    def "zera os indicadores quando não há galerias no período"() {
        when: "não há dados"
        def resultado = GaleriaPerformanceCalculator.calcular(LocalDate.of(2026, 10, 27), 3, List.of())

        then: "os totais ficam zerados e os SLAs sem amostra"
        resultado.totalGalerias() == 0
        resultado.publicadas() == 0
        resultado.comercializadas() == 0
        resultado.atrasadas() == 0
        resultado.tempoMedioPublicacaoDias() == null
        resultado.tempoMedioEdicaoDias() == null
        resultado.tempoMedioEntregaDias() == null
        resultado.receitaExtras().compareTo(BigDecimal.ZERO) == 0
        resultado.ticketMedioExtras().compareTo(BigDecimal.ZERO) == 0
        resultado.extrasPorGaleria().compareTo(BigDecimal.ZERO) == 0
        resultado.conversaoExtras() == 0.0
        resultado.serieMensal().size() == 3
        resultado.serieMensal().every { it.galerias() == 0 }
    }
}
