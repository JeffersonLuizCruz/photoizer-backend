package com.photoizer.crm.financeiro.service

import com.photoizer.crm.agenda.model.StatusAgendamento
import com.photoizer.crm.agenda.repository.AgendamentoRepository
import com.photoizer.crm.ecommerce.model.CompraExtra
import com.photoizer.crm.ecommerce.model.MetodoPagamento
import com.photoizer.crm.ecommerce.model.StatusCompraExtra
import com.photoizer.crm.ecommerce.repository.CompraExtraRepository
import com.photoizer.crm.financeiro.repository.PagamentoRepository
import com.photoizer.crm.pacote.repository.PacoteRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import spock.lang.Specification

import static com.photoizer.crm.agenda.support.AgendaFixtures.agendamento
import static com.photoizer.crm.agenda.support.AgendaFixtures.pacote

@SpringBootTest
@Transactional
@TestPropertySource(properties = ["spring.datasource.url=jdbc:h2:mem:compra-extra-financeiro;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"])
class CompraExtraFinanceiroIntegrationSpec extends Specification {

    @Autowired
    AgendamentoRepository agendamentoRepository

    @Autowired
    PacoteRepository pacoteRepository

    @Autowired
    CompraExtraRepository compraExtraRepository

    @Autowired
    PagamentoRepository pagamentoRepository

    @Autowired
    CompraExtraFinanceiroService service

    def "checkout e confirmação refletem no agendamento e no pagamento"() {
        given: "um agendamento com R\$ 700,00 de saldo"
        def agendamento = agendamentoSalvo()
        def compra = compraExtraRepository.save(CompraExtra.builder()
            .agendamentoId(agendamento.id)
            .valorTotal(new BigDecimal("200.00"))
            .quantidadeFotos(2)
            .metodoPagamento(MetodoPagamento.PIX)
            .status(StatusCompraExtra.AGUARDANDO_COMPROVANTE)
            .build())

        when: "o checkout é contabilizado"
        service.contabilizarCriacao(compra)
        def aposCriacao = agendamentoRepository.findById(agendamento.id).get()

        then: "os extras entram no total final e no saldo"
        aposCriacao.valorExtras == new BigDecimal("200.00")
        aposCriacao.valorTotalFinal == new BigDecimal("1200.00")
        aposCriacao.valorRestante == new BigDecimal("900.00")

        when: "o pagamento é confirmado"
        compra.status = StatusCompraExtra.PAGA
        compraExtraRepository.save(compra)
        service.contabilizarPagamento(compra)
        def aposPagamento = agendamentoRepository.findById(agendamento.id).get()
        def pagamento = pagamentoRepository.findByCompraExtraId(compra.id)

        then: "o recebimento entra na entrada paga e o saldo volta a ser o do pacote"
        aposPagamento.valorEntradaPago == new BigDecimal("500.00")
        aposPagamento.valorRestante == new BigDecimal("700.00")
        pagamento.isPresent()
        pagamento.get().valor == new BigDecimal("200.00")
        pagamento.get().tipoPagamento.name() == "EXTRA"
        pagamento.get().formaPagamento.name() == "PIX"
    }

    def "cancelamento estorna os extras do agendamento"() {
        given: "uma compra pendente já contabilizada"
        def agendamento = agendamentoSalvo()
        def compra = compraExtraRepository.save(CompraExtra.builder()
            .agendamentoId(agendamento.id)
            .valorTotal(new BigDecimal("150.00"))
            .quantidadeFotos(1)
            .metodoPagamento(MetodoPagamento.PIX)
            .status(StatusCompraExtra.AGUARDANDO_COMPROVANTE)
            .build())
        service.contabilizarCriacao(compra)

        when: "a compra é cancelada"
        compra.status = StatusCompraExtra.CANCELADA
        compraExtraRepository.save(compra)
        service.contabilizarCancelamento(compra)
        def atualizado = agendamentoRepository.findById(agendamento.id).get()

        then: "o total final e o saldo voltam ao valor do pacote"
        atualizado.valorExtras == BigDecimal.ZERO
        atualizado.valorTotalFinal == new BigDecimal("1000.00")
        atualizado.valorRestante == new BigDecimal("700.00")
        !compra.extrasContabilizados
    }

    private com.photoizer.crm.agenda.model.Agendamento agendamentoSalvo() {
        def pacote = pacoteRepository.save(pacote(id: null))
        agendamentoRepository.save(agendamento(
            id: null,
            pacote: pacote,
            status: StatusAgendamento.CONFIRMADO,
            valorTotal: new BigDecimal("1000.00"),
            valorEntradaExigido: new BigDecimal("300.00"),
            valorEntradaPago: new BigDecimal("300.00"),
            valorRestante: new BigDecimal("700.00"),
            valorExtras: BigDecimal.ZERO,
            valorTotalFinal: new BigDecimal("1000.00"),
            taxaDeslocamento: BigDecimal.ZERO,
            percentualEntrada: new BigDecimal("30.00")))
    }
}
