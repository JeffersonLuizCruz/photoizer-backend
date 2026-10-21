package com.photoizer.crm.agenda.model

import com.photoizer.crm.agenda.exception.StatusAgendamentoInvalidoException
import com.photoizer.crm.agenda.support.AgendaFixtures
import com.photoizer.crm.shared.exception.BadRequestException
import com.photoizer.crm.shared.model.FormaPagamento
import spock.lang.Specification

import java.time.LocalDateTime

import static com.photoizer.crm.agenda.support.AgendaFixtures.agendamento

class AgendamentoDomainSpec extends Specification {

    def "transição para REALIZADO registra a data de realização"() {
        given: "um agendamento confirmado"
        def agendamento = agendamento(status: StatusAgendamento.CONFIRMADO)

        when: "o ensaio é realizado"
        agendamento.transicionarPara(StatusAgendamento.REALIZADO)

        then: "o status e a data de realização são atualizados"
        agendamento.status == StatusAgendamento.REALIZADO
        agendamento.dataRealizacao != null
    }

    def "transição para FINALIZADO registra a data de finalização"() {
        given: "um agendamento em edição"
        def agendamento = agendamento(status: StatusAgendamento.EM_EDICAO)

        when: "o ensaio é finalizado"
        agendamento.transicionarPara(StatusAgendamento.FINALIZADO)

        then: "o status e a data de finalização são atualizados"
        agendamento.status == StatusAgendamento.FINALIZADO
        agendamento.dataFinalizacao != null
    }

    def "rejeita transição fora do fluxo"() {
        given: "um agendamento finalizado"
        def agendamento = agendamento(status: StatusAgendamento.FINALIZADO)

        when: "tenta voltar para CONFIRMADO"
        agendamento.transicionarPara(StatusAgendamento.CONFIRMADO)

        then: "a transição é bloqueada"
        thrown(StatusAgendamentoInvalidoException)
        agendamento.status == StatusAgendamento.FINALIZADO
    }

    def "assinatura pública vincula cliente e transita para aguardando aprovação"() {
        given: "uma proposta em pré-reserva"
        def agendamento = agendamento(status: StatusAgendamento.PRE_RESERVA)
        def cliente = AgendaFixtures.cliente()

        when: "o cliente assina a proposta"
        agendamento.assinarProposta(
            cliente, "true", "uploads/comprovante.pdf", "Maria Souza",
            "{}", "hash", "uploads/termo.pdf", null)

        then: "o cliente, a autorização e a assinatura são registrados"
        agendamento.status == StatusAgendamento.AGUARDANDO_APROVACAO
        agendamento.cliente == cliente
        agendamento.autorizaUsoImagem
        agendamento.urlComprovanteEntrada == "uploads/comprovante.pdf"
        agendamento.snapshotHash == "hash"
        agendamento.urlPdfAssinatura == "uploads/termo.pdf"
        agendamento.dataAssinatura != null
    }

    def "confirma o pagamento da entrada"() {
        given: "uma proposta assinada"
        def agendamento = agendamento(status: StatusAgendamento.AGUARDANDO_APROVACAO)

        when: "o pagamento é confirmado"
        agendamento.confirmarPagamento()

        then: "o agendamento aguarda aprovação"
        agendamento.status == StatusAgendamento.PAGAMENTO_CONFIRMADO
    }

    def "aprovação quita a entrada e recalcula o saldo"() {
        given: "um agendamento com pagamento confirmado"
        def agendamento = agendamento(
            status: StatusAgendamento.PAGAMENTO_CONFIRMADO,
            valorTotal: new BigDecimal("1000.00"),
            valorTotalFinal: new BigDecimal("1000.00"),
            valorEntradaExigido: new BigDecimal("300.00"))

        when: "a proposta é aprovada"
        agendamento.aprovar()

        then: "o status é confirmado e a entrada é baixada"
        agendamento.status == StatusAgendamento.CONFIRMADO
        agendamento.dataConfirmacao != null
        agendamento.valorEntradaPago == new BigDecimal("300.00")
        agendamento.valorRestante == new BigDecimal("700.00")
    }

    def "recusa proposta em análise registrando motivo e data"() {
        given: "uma proposta em pré-reserva"
        def agendamento = agendamento(status: StatusAgendamento.PRE_RESERVA)

        when: "a proposta é recusada"
        agendamento.recusar("Data indisponível")

        then: "o agendamento é cancelado com o motivo"
        agendamento.status == StatusAgendamento.CANCELADO
        agendamento.motivoRecusa == "Data indisponível"
        agendamento.dataRecusa != null
    }

    def "não permite recusar proposta fora da análise"() {
        given: "um agendamento já confirmado"
        def agendamento = agendamento(status: StatusAgendamento.CONFIRMADO)

        when: "tenta recusar a proposta"
        agendamento.recusar("Duplicada")

        then: "a operação é bloqueada"
        thrown(BadRequestException)
        agendamento.status == StatusAgendamento.CONFIRMADO
    }

    def "reagenda o ensaio e mantém o status confirmado"() {
        given: "um agendamento confirmado"
        def agendamento = agendamento(status: StatusAgendamento.CONFIRMADO)
        def novaData = LocalDateTime.now().plusDays(20)

        when: "o ensaio é reagendado"
        agendamento.reagendar(novaData, 120)

        then: "a nova data, duração e confirmação são aplicadas"
        agendamento.dataHoraEnsaio == novaData
        agendamento.duracaoMinutos == 120
        agendamento.status == StatusAgendamento.CONFIRMADO
        agendamento.dataConfirmacao != null
    }

    def "aplica pagamento final e transita para edição"() {
        given: "um agendamento aguardando pagamento final"
        def agendamento = agendamento(
            status: StatusAgendamento.AGUARDANDO_PAGAMENTO_FINAL,
            valorTotalFinal: new BigDecimal("1000.00"),
            valorRestante: new BigDecimal("700.00"))

        when: "o pagamento final é registrado"
        agendamento.aplicarPagamentoFinal("uploads/final.pdf", FormaPagamento.PIX)

        then: "o saldo é zerado e o ensaio entra em edição"
        agendamento.status == StatusAgendamento.EM_EDICAO
        agendamento.valorRestante == BigDecimal.ZERO
        agendamento.valorEntradaPago == new BigDecimal("1000.00")
        agendamento.formaPagamentoFinal == FormaPagamento.PIX
        agendamento.dataPagamentoFinal != null
    }

    def "alterna o destaque do ensaio"() {
        given: "um ensaio sem destaque"
        def agendamento = agendamento(ensaioDestaque: false)

        when: "o destaque é alternado duas vezes"
        agendamento.alternarDestaque()
        def primeiro = agendamento.ensaioDestaque
        agendamento.alternarDestaque()

        then: "o estado original é restaurado"
        primeiro
        !agendamento.ensaioDestaque
    }

    def "registra pagamento parcial sem alterar o status"() {
        given: "um agendamento confirmado com saldo"
        def agendamento = agendamento(
            status: StatusAgendamento.CONFIRMADO,
            valorEntradaPago: new BigDecimal("300.00"),
            valorRestante: new BigDecimal("700.00"))

        when: "um pagamento de R\$ 200,00 é registrado"
        agendamento.registrarPagamento(new BigDecimal("200.00"))

        then: "o saldo é reduzido e o status é preservado"
        agendamento.valorEntradaPago == new BigDecimal("500.00")
        agendamento.valorRestante == new BigDecimal("500.00")
        agendamento.status == StatusAgendamento.CONFIRMADO
    }

    def "quitação do saldo transita para aguardando pagamento final"() {
        given: "um agendamento confirmado com saldo de R\$ 100,00"
        def agendamento = agendamento(
            status: StatusAgendamento.CONFIRMADO,
            valorEntradaPago: new BigDecimal("900.00"),
            valorRestante: new BigDecimal("100.00"))

        when: "o saldo é quitado"
        agendamento.registrarPagamento(new BigDecimal("100.00"))

        then: "o status avança para aguardando pagamento final"
        agendamento.valorRestante == BigDecimal.ZERO
        agendamento.status == StatusAgendamento.AGUARDANDO_PAGAMENTO_FINAL
    }

    def "pagamento de e-commerce reduz o saldo sem alterar a entrada do pacote"() {
        given: "um agendamento com extras"
        def agendamento = agendamento(
            status: StatusAgendamento.CONFIRMADO,
            valorEntradaPago: new BigDecimal("300.00"),
            valorRestante: new BigDecimal("700.00"))

        when: "uma compra extra é paga"
        agendamento.registrarPagamentoEcommerce(new BigDecimal("50.00"))

        then: "somente o saldo é reduzido"
        agendamento.valorEntradaPago == new BigDecimal("300.00")
        agendamento.valorRestante == new BigDecimal("650.00")
    }

    def "adiciona extras recalculando o total final"() {
        given: "um agendamento de R\$ 1000,00 com entrada de R\$ 300,00"
        def agendamento = agendamento(
            valorTotal: new BigDecimal("1000.00"),
            valorTotalFinal: new BigDecimal("1000.00"),
            valorEntradaPago: new BigDecimal("300.00"),
            valorRestante: new BigDecimal("700.00"))

        when: "R\$ 200,00 em extras são adicionados"
        agendamento.adicionarExtras(new BigDecimal("200.00"))

        then: "o total final e o saldo são recalculados"
        agendamento.valorExtras == new BigDecimal("200.00")
        agendamento.valorTotalFinal == new BigDecimal("1200.00")
        agendamento.valorRestante == new BigDecimal("900.00")
    }

    def "define token público e registra envio da proposta"() {
        given: "um agendamento sem token público"
        def agendamento = agendamento()
        def expiracao = LocalDateTime.now().plusDays(7)

        when: "o token e o envio são registrados"
        agendamento.definirTokenProposta("token", "hash", expiracao)
        agendamento.registrarEnvioProposta()

        then: "os dados do link público ficam disponíveis"
        agendamento.tokenProposta == "token"
        agendamento.tokenPropostaHash == "hash"
        agendamento.tokenPropostaExpiracao == expiracao
        agendamento.dataEnvioProposta != null
    }

    def "usa o nome do cliente ou o rótulo padrão de pré-reserva"() {
        given: "um agendamento sem cliente e outro com cliente"
        def semCliente = agendamento()
        def comCliente = agendamento(cliente: AgendaFixtures.cliente(nome: "Ana"))

        expect: "o nome é resolvido com fallback seguro"
        semCliente.nomeCliente() == "Pré-reserva"
        comCliente.nomeCliente() == "Ana"
    }
}
