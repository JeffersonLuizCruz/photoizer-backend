package com.photoizer.crm.agenda.service

import com.photoizer.crm.shared.exception.BadRequestException
import com.photoizer.crm.shared.model.TipoRepasse
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class AgendamentoValoresCalculatorSpec extends Specification {

    @Subject
    def calculator = new AgendamentoValoresCalculator()

    def "aplica entrada mínima de um centavo quando o percentual arredonda para zero"() {
        given: "um pacote de R\$ 0,01 e entrada de 30%"
        def valorBase = new BigDecimal("0.01")

        when: "os valores de um novo agendamento são calculados"
        def valores = calculator.calcularValoresNovo(valorBase, BigDecimal.ZERO, new BigDecimal("30.00"))

        then: "a entrada é elevada ao mínimo de um centavo"
        valores.valorTotal() == new BigDecimal("0.01")
        valores.valorEntradaExigido() == new BigDecimal("0.01")
        valores.valorRestante() == new BigDecimal("0.00")
    }

    def "aplica entrada mínima também no recálculo da proposta"() {
        given: "um pacote de R\$ 0,01 sem pagamento e sem extras"
        def valorBase = new BigDecimal("0.01")

        when: "os valores são recalculados"
        def valores = calculator.calcularValoresAtualizacao(
            valorBase, BigDecimal.ZERO, new BigDecimal("30.00"), BigDecimal.ZERO, BigDecimal.ZERO)

        then: "a entrada exigida nunca fica abaixo de um centavo"
        valores.valorEntradaExigido() == new BigDecimal("0.01")
    }

    @Unroll
    def "calcula total e entrada com percentual #percentual e deslocamento #deslocamento"() {
        given: "um pacote de valor conhecido"
        def valorBase = new BigDecimal("2000.00")
        def taxa = new BigDecimal(deslocamento)
        def percentualEntrada = new BigDecimal(percentual)

        when: "os valores são calculados"
        def valores = calculator.calcularValoresNovo(valorBase, taxa, percentualEntrada)

        then: "o total e a entrada refletem os parâmetros"
        valores.valorTotal() == new BigDecimal(totalEsperado)
        valores.valorEntradaExigido() == new BigDecimal(entradaEsperada)

        where:
        deslocamento | percentual | totalEsperado | entradaEsperada
        "0.00"       | "30.00"    | "2000.00"     | "600.00"
        "100.00"     | "30.00"    | "2100.00"     | "630.00"
        "0.00"       | "100.00"   | "2000.00"     | "2000.00"
    }

    def "não deixa a entrada ultrapassar o valor total"() {
        given: "um pacote de R\$ 0,01 e entrada de 100%"
        def valorBase = new BigDecimal("0.01")

        when: "os valores são calculados"
        def valores = calculator.calcularValoresNovo(valorBase, BigDecimal.ZERO, new BigDecimal("100.00"))

        then: "a entrada é limitada ao valor total"
        valores.valorEntradaExigido() == new BigDecimal("0.01")
    }

    def "rejeita valor total não positivo"() {
        when: "o cálculo recebe valor base zero"
        calculator.calcularValoresNovo(BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("30.00"))

        then: "uma exceção de regra de negócio é lançada"
        thrown(BadRequestException)
    }

    @Unroll
    def "calcula repasse #tipo com valor #valor e percentual #percentual"() {
        expect: "o valor efetivo do repasse é calculado corretamente"
        calculator.valorRepasseEfetivo(
            new BigDecimal("1000.00"), TipoRepasse.valueOf(tipo), valor as BigDecimal, percentual as BigDecimal
        ) == new BigDecimal(esperado)

        where:
        tipo         | valor         | percentual | esperado
        "FIXO"       | "250.00"      | null       | "250.00"
        "PERCENTUAL" | null          | "10.00"    | "100.00"
        "PERCENTUAL" | "250.00"      | "15.00"    | "150.00"
    }

    def "rejeita repasse percentual sem percentual informado"() {
        when: "o cálculo tenta um repasse percentual sem percentual"
        calculator.calcularValorRepasse(
            new BigDecimal("1000.00"), TipoRepasse.PERCENTUAL, null, null)

        then: "uma exceção de regra de negócio é lançada"
        thrown(BadRequestException)
    }
}
