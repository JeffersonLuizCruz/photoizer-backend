package com.photoizer.crm.financeiro.service

import com.photoizer.crm.agenda.model.StatusAgendamento
import com.photoizer.crm.agenda.repository.AgendamentoRepository
import com.photoizer.crm.ecommerce.model.CompraExtra
import com.photoizer.crm.ecommerce.model.MetodoPagamento
import com.photoizer.crm.ecommerce.model.StatusCompraExtra
import com.photoizer.crm.ecommerce.repository.CompraExtraRepository
import com.photoizer.crm.financeiro.event.ExtrasAdicionadosEvent
import com.photoizer.crm.financeiro.event.ExtrasRemovidosEvent
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification
import spock.lang.Subject

import static com.photoizer.crm.agenda.support.AgendaFixtures.agendamento

class CompraExtraFinanceiroServiceSpec extends Specification {

    def compraRepository = Mock(CompraExtraRepository)
    def agendamentoRepository = Mock(AgendamentoRepository)
    def pagamentoService = Mock(PagamentoService)
    def eventPublisher = Mock(ApplicationEventPublisher)

    @Subject
    def service = new CompraExtraFinanceiroService(
        compraRepository, agendamentoRepository, pagamentoService, eventPublisher)

    def setup() {
        agendamentoRepository.findById(_) >> Optional.of(
            agendamento(status: StatusAgendamento.CONFIRMADO))
        compraRepository.save(_) >> { args -> args[0] }
    }

    def "checkout contabiliza o extra no total final e no saldo"() {
        given: "uma compra pendente de R\$ 15,00"
        def compra = compra(StatusCompraExtra.AGUARDANDO_COMPROVANTE)

        when: "a criação é contabilizada"
        service.contabilizarCriacao(compra)

        then: "o evento de extras é publicado e a flag é marcada"
        1 * eventPublisher.publishEvent({ ExtrasAdicionadosEvent event ->
            event.agendamentoId() == compra.agendamentoId
                && event.quantidade() == 1
                && event.valorTotal() == new BigDecimal("15.00")
        })
        1 * compraRepository.save(compra)
        compra.extrasContabilizados
    }

    def "criação não contabiliza duas vezes a mesma compra"() {
        given: "uma compra já contabilizada"
        def compra = compra(StatusCompraExtra.AGUARDANDO_COMPROVANTE)
        compra.extrasContabilizados = true

        when: "a criação é processada novamente"
        service.contabilizarCriacao(compra)

        then: "nada é publicado nem salvo"
        0 * eventPublisher.publishEvent(_)
        0 * compraRepository.save(_)
    }

    def "confirmação aplica o recebimento uma única vez"() {
        given: "uma compra paga ainda não contabilizada"
        def compra = compra(StatusCompraExtra.PAGA)

        when: "a confirmação é processada"
        service.contabilizarPagamento(compra)

        then: "os extras são contabilizados e o pagamento é registrado"
        1 * eventPublisher.publishEvent(_ as ExtrasAdicionadosEvent)
        1 * pagamentoService.registrarPagamentoExtraEcommerce(
            compra.agendamentoId, new BigDecimal("15.00"), compra.id, MetodoPagamento.PIX)
        compra.extrasContabilizados
        compra.pagamentoContabilizado
    }

    def "confirmação não aplica recebimento duplicado"() {
        given: "uma compra paga já contabilizada"
        def compra = compra(StatusCompraExtra.PAGA)
        compra.extrasContabilizados = true
        compra.pagamentoContabilizado = true

        when: "a confirmação é processada novamente"
        service.contabilizarPagamento(compra)

        then: "nada é publicado nem pago"
        0 * eventPublisher.publishEvent(_)
        0 * pagamentoService.registrarPagamentoExtraEcommerce(_, _, _, _)
    }

    def "cancelamento estorna os extras contabilizados"() {
        given: "uma compra pendente já contabilizada"
        def compra = compra(StatusCompraExtra.CANCELADA)
        compra.extrasContabilizados = true

        when: "o cancelamento é processado"
        service.contabilizarCancelamento(compra)

        then: "o evento de estorno é publicado e a flag é limpa"
        1 * eventPublisher.publishEvent({ ExtrasRemovidosEvent event ->
            event.agendamentoId() == compra.agendamentoId
                && event.valorTotal() == new BigDecimal("15.00")
        })
        1 * compraRepository.save(compra)
        !compra.extrasContabilizados
    }

    def "cancelamento não estorna compra paga"() {
        given: "uma compra cancelada com pagamento já contabilizado"
        def compra = compra(StatusCompraExtra.CANCELADA)
        compra.extrasContabilizados = true
        compra.pagamentoContabilizado = true

        when: "o cancelamento é processado"
        service.contabilizarCancelamento(compra)

        then: "nenhum estorno é aplicado"
        0 * eventPublisher.publishEvent(_)
        0 * compraRepository.save(_)
    }

    private CompraExtra compra(StatusCompraExtra status) {
        CompraExtra.builder()
            .id(UUID.randomUUID())
            .agendamentoId(UUID.randomUUID())
            .valorTotal(new BigDecimal("15.00"))
            .quantidadeFotos(1)
            .metodoPagamento(MetodoPagamento.PIX)
            .status(status)
            .build()
    }
}
