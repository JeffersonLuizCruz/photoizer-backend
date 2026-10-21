package com.photoizer.crm.agenda.service

import com.photoizer.crm.agenda.model.RepasseStatus
import com.photoizer.crm.agenda.repository.AgendamentoFotografoRepository
import com.photoizer.crm.agenda.repository.AgendamentoRepository
import com.photoizer.crm.despesa.service.DespesaService
import com.photoizer.crm.shared.exception.BadRequestException
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.photoizer.crm.agenda.support.AgendaFixtures.agendamento
import static com.photoizer.crm.agenda.support.AgendaFixtures.repasse

class PartilhaServiceSpec extends Specification {

    def agendamentoFotografoRepository = Mock(AgendamentoFotografoRepository)
    def agendamentoRepository = Mock(AgendamentoRepository)
    def despesaService = Mock(DespesaService)

    @Subject
    def service = new PartilhaService(
        agendamentoFotografoRepository, agendamentoRepository, despesaService)

    def "calcula partilha e lucro do CRM a partir das despesas e repasses"() {
        given: "um ensaio de R\$ 1000,00, R\$ 100,00 de custos e R\$ 300,00 de repasses"
        def agendamento = agendamento(valorTotalFinal: new BigDecimal("1000.00"))
        despesaService.somarCustosTodosFotografos(agendamento.id) >> new BigDecimal("100.00")
        agendamentoFotografoRepository.findByAgendamentoId(agendamento.id) >> [
            repasse(agendamento, null, RepasseStatus.PENDENTE, new BigDecimal("200.00")),
            repasse(agendamento, null, RepasseStatus.PENDENTE, new BigDecimal("100.00"))
        ]

        when: "a partilha é recalculada"
        service.calcularPartilhaFotografo(agendamento)

        then: "a partilha global e o lucro são persistidos"
        agendamento.valorPartilhaGlobal == new BigDecimal("900.00")
        agendamento.valorLucroCrm == new BigDecimal("600.00")
        1 * agendamentoRepository.save(agendamento)
    }

    def "ignora repasses cancelados no cálculo da partilha"() {
        given: "um ensaio com um repasse ativo e outro cancelado"
        def agendamento = agendamento(valorTotalFinal: new BigDecimal("1000.00"))
        despesaService.somarCustosTodosFotografos(agendamento.id) >> BigDecimal.ZERO
        agendamentoFotografoRepository.findByAgendamentoId(agendamento.id) >> [
            repasse(agendamento, null, RepasseStatus.PENDENTE, new BigDecimal("250.00")),
            repasse(agendamento, null, RepasseStatus.CANCELADO, new BigDecimal("999.00"))
        ]

        when: "a partilha é recalculada"
        service.calcularPartilhaFotografo(agendamento)

        then: "somente o repasse ativo é considerado"
        agendamento.valorPartilhaGlobal == new BigDecimal("1000.00")
        agendamento.valorLucroCrm == new BigDecimal("750.00")
    }

    def "bloqueia soma de repasses maior que a partilha do ensaio"() {
        given: "um ensaio de R\$ 1000,00 e repasses de R\$ 1200,00"
        def agendamento = agendamento(valorTotalFinal: new BigDecimal("1000.00"))
        despesaService.somarCustosTodosFotografos(agendamento.id) >> BigDecimal.ZERO
        agendamentoFotografoRepository.findByAgendamentoId(agendamento.id) >> [
            repasse(agendamento, null, RepasseStatus.PENDENTE, new BigDecimal("1200.00"))
        ]

        when: "a partilha é recalculada"
        service.calcularPartilhaFotografo(agendamento)

        then: "o cálculo é bloqueado"
        def exception = thrown(BadRequestException)
        exception.message.contains("excede a partilha")
        0 * agendamentoRepository.save(_)
    }

    def "validarPartilha não falha quando o agendamento não existe"() {
        given: "um agendamento inexistente"
        def agendamentoId = UUID.randomUUID()
        agendamentoRepository.findById(agendamentoId) >> Optional.empty()

        when: "a partilha é validada"
        service.validarPartilha(agendamentoId)

        then: "nenhuma consulta adicional é feita"
        noExceptionThrown()
        0 * despesaService.somarCustosTodosFotografos(_)
    }

    @Unroll
    def "validarPartilha aceita repasses de #repasses dentro da partilha"() {
        given: "um agendamento com custos zerados"
        def agendamento = agendamento(valorTotalFinal: new BigDecimal("1000.00"))
        agendamentoRepository.findById(agendamento.id) >> Optional.of(agendamento)
        despesaService.somarCustosTodosFotografos(agendamento.id) >> BigDecimal.ZERO
        agendamentoFotografoRepository.findByAgendamentoId(agendamento.id) >> [
            repasse(agendamento, null, RepasseStatus.PENDENTE, new BigDecimal(repasses))
        ]

        when: "a partilha é validada"
        service.validarPartilha(agendamento.id)

        then: "nenhuma exceção é lançada"
        noExceptionThrown()

        where:
        repasses << ["900.00", "1000.00"]
    }

    def "validarPartilha bloqueia repasses acima da partilha"() {
        given: "um agendamento com repasses acima do valor do ensaio"
        def agendamento = agendamento(valorTotalFinal: new BigDecimal("1000.00"))
        agendamentoRepository.findById(agendamento.id) >> Optional.of(agendamento)
        despesaService.somarCustosTodosFotografos(agendamento.id) >> BigDecimal.ZERO
        agendamentoFotografoRepository.findByAgendamentoId(agendamento.id) >> [
            repasse(agendamento, null, RepasseStatus.PENDENTE, new BigDecimal("1000.01"))
        ]

        when: "a partilha é validada"
        service.validarPartilha(agendamento.id)

        then: "o limite é reportado"
        def exception = thrown(BadRequestException)
        exception.message.contains("excede a partilha")
    }
}
