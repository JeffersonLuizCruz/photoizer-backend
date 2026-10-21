package com.photoizer.crm.financeiro.service

import com.photoizer.crm.agenda.model.Agendamento
import com.photoizer.crm.agenda.model.OrigemAgendamento
import com.photoizer.crm.agenda.model.RepasseStatus
import com.photoizer.crm.agenda.model.StatusAgendamento
import com.photoizer.crm.agenda.repository.AgendamentoFotografoRepository
import com.photoizer.crm.agenda.repository.AgendamentoRepository
import com.photoizer.crm.comissao.repository.IndicacaoRepository
import com.photoizer.crm.config.service.ConfiguracaoService
import com.photoizer.crm.despesa.api.DespesaMapper
import com.photoizer.crm.despesa.repository.DespesaRepository
import com.photoizer.crm.financeiro.model.StatusReceita
import com.photoizer.crm.financeiro.model.TipoServico
import com.photoizer.crm.financeiro.repository.PagamentoRepository
import com.photoizer.crm.financeiro.repository.ReceitaRepository
import com.photoizer.crm.pacote.service.PacoteQueryService
import com.photoizer.crm.shared.model.FormaPagamento
import com.photoizer.crm.shared.port.DisplacementCostPort
import com.photoizer.crm.shared.port.RepasseAggregationPort
import com.photoizer.crm.shared.port.StatusClassificationPort
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.jpa.domain.Specification as JpaSpecification
import spock.lang.Specification
import spock.lang.Subject

import java.time.LocalDate
import java.time.LocalDateTime

import static com.photoizer.crm.agenda.support.AgendaFixtures.agendamento
import static com.photoizer.crm.agenda.support.AgendaFixtures.pacote

class FinanceiroAvulsoSpec extends Specification {

    def agendamentoRepository = Mock(AgendamentoRepository)
    def agendamentoFotografoRepository = Mock(AgendamentoFotografoRepository)
    def pacoteQueryService = Mock(PacoteQueryService)
    def indicacaoRepository = Mock(IndicacaoRepository)
    def despesaRepository = Mock(DespesaRepository)
    def receitaRepository = Mock(ReceitaRepository)
    def pagamentoRepository = Mock(PagamentoRepository)
    def configuracaoService = Mock(ConfiguracaoService)
    def statusClassificationPort = Mock(StatusClassificationPort)
    def displacementCostPort = Mock(DisplacementCostPort)
    def repasseAggregationPort = Mock(RepasseAggregationPort)
    def despesaMapper = Mock(DespesaMapper)

    @Subject
    def queryService = new FinanceiroQueryService(
        agendamentoRepository,
        agendamentoFotografoRepository,
        pacoteQueryService,
        indicacaoRepository,
        despesaRepository,
        receitaRepository,
        pagamentoRepository,
        configuracaoService,
        statusClassificationPort,
        displacementCostPort,
        repasseAggregationPort,
        despesaMapper)

    @Subject
    def dashboardService = new FinanceiroDashboardService(
        receitaRepository,
        despesaRepository,
        agendamentoRepository,
        agendamentoFotografoRepository,
        indicacaoRepository,
        pagamentoRepository,
        statusClassificationPort,
        displacementCostPort,
        repasseAggregationPort)

    def setup() {
        statusClassificationPort.statusIgnorados() >> Set.of(
            StatusAgendamento.CANCELADO, StatusAgendamento.NO_SHOW)
        displacementCostPort.deslocamentoEfetivo(_) >> BigDecimal.ZERO
        repasseAggregationPort.carregarRepasses() >> new RepasseAggregationPort.RepassesResumo(Map.of(), Map.of())
        receitaRepository.findAll(_ as JpaSpecification) >> []
        receitaRepository.findAll() >> []
        despesaRepository.findAll() >> []
        indicacaoRepository.findByAgendamentoIdIn(_) >> []
        agendamentoFotografoRepository.findByStatusWithAgendamento(RepasseStatus.PENDENTE) >> []
        agendamentoFotografoRepository.findByStatusWithAgendamento(RepasseStatus.PAGO) >> []
    }

    def "fluxo de caixa inclui o restante do avulso percentual como entrada prevista"() {
        given: "um avulso confirmado no modo percentual"
        def avulso = avulsoPercentual()
        agendamentoRepository.findByDataBetween(_, _, _) >> [avulso]

        when: "o fluxo é calculado"
        def fluxo = queryService.calcularFluxoCaixa(LocalDate.now(), LocalDate.now().plusDays(30), "MENSAL")

        then: "o restante entra como receita prevista e nada foi realizado"
        fluxo.entradasRealizadas == BigDecimal.ZERO
        fluxo.entradasPrevistasTotal == new BigDecimal("1000.00")
        fluxo.itens.any {
            it.origem() == "AGENDAMENTO" && it.tipo() == "RECEITA"
                && it.valor() == new BigDecimal("1000.00") && it.status() == "PENDENTE"
        }
    }

    def "fluxo de caixa separa entrada recebida e restante do avulso"() {
        given: "um avulso com entrada já recebida"
        def avulso = avulsoRecebido()
        agendamentoRepository.findByDataBetween(_, _, _) >> [avulso]

        when: "o fluxo é calculado"
        def fluxo = queryService.calcularFluxoCaixa(LocalDate.now(), LocalDate.now().plusDays(30), "MENSAL")

        then: "a entrada é realizada e somente o restante é previsto"
        fluxo.entradasRealizadas == new BigDecimal("300.00")
        fluxo.entradasPrevistasTotal == new BigDecimal("700.00")
    }

    def "dashboard reflete o avulso percentual e o recebido nos cards"() {
        given: "um avulso percentual e outro com entrada recebida"
        def percentual = avulsoPercentual()
        def recebido = avulsoRecebido()
        agendamentoRepository.findAll() >> [percentual, recebido]

        when: "o dashboard é calculado"
        def dashboard = dashboardService.calcular(null, null, null, null, null, null)

        then: "o valor bruto e a receber consideram os dois avulsos"
        dashboard.cards.qtdTrabalhos == 2
        dashboard.cards.valorBruto == new BigDecimal("2000.00")
        dashboard.cards.aReceber == new BigDecimal("1700.00")
        dashboard.cards.detalhamento.entradaEnsaios == new BigDecimal("300.00")
        dashboard.cards.detalhamento.restanteEnsaios == new BigDecimal("1700.00")
    }

    def "filtros de tipo e status separam avulsos no dashboard"() {
        given: "um avulso percentual"
        def percentual = avulsoPercentual()
        agendamentoRepository.findAll() >> [percentual]

        when: "filtra por tipo diferente de ensaio"
        def semEnsaios = dashboardService.calcular(null, null, TipoServico.CASAMENTO, null, null, null)

        then: "o agendamento é removido"
        semEnsaios.cards.qtdTrabalhos == 0
        semEnsaios.cards.valorBruto == BigDecimal.ZERO

        when: "filtra por pendente"
        def pendentes = dashboardService.calcular(null, null, TipoServico.ENSAIO, StatusReceita.PENDENTE, null, null)

        then: "o avulso percentual é incluído"
        pendentes.cards.qtdTrabalhos == 1
        pendentes.cards.aReceber == new BigDecimal("1000.00")
    }

    def "inadimplência inclui avulso com ensaio vencido e saldo em aberto"() {
        given: "um avulso confirmado com data no passado e saldo devedor"
        def avulso = avulsoPercentual()
        avulso.dataHoraEnsaio = LocalDateTime.now().minusDays(5)
        receitaRepository.findInadimplentes(_, _) >> []
        agendamentoRepository.findInadimplentes(_, _) >> [avulso]

        def relatorioService = new FinanceiroRelatorioService(
            receitaRepository, despesaRepository, agendamentoRepository, statusClassificationPort)

        when: "o relatório de inadimplência é calculado"
        def relatorio = relatorioService.inadimplencia(null, null)

        then: "o avulso aparece com o saldo em aberto"
        relatorio.totalEmAberto == new BigDecimal("1000.00")
        relatorio.itens.size() == 1
        relatorio.itens.first().tipoServico() == TipoServico.ENSAIO
        relatorio.itens.first().valorEmAberto() == new BigDecimal("1000.00")
        relatorio.itens.first().diasAtraso() == 5
    }

    def "pagamento manual registra tipo e forma e atualiza o evento"() {
        given: "um avulso confirmado"
        def avulso = avulsoPercentual()
        def eventPublisher = Mock(ApplicationEventPublisher)
        def fileStorage = Mock(com.photoizer.crm.shared.storage.FileStorageService)
        def fileValidator = Mock(com.photoizer.crm.shared.storage.FileValidator)
        def service = new PagamentoService(
            pagamentoRepository, agendamentoRepository, eventPublisher, fileStorage, fileValidator)

        agendamentoRepository.findById(avulso.id) >> Optional.of(avulso)
        pagamentoRepository.findByAgendamentoIdAndValorAndCompraExtraIdIsNull(avulso.id, new BigDecimal("100.00")) >> Optional.empty()
        pagamentoRepository.save(_) >> { args -> args[0] }

        when: "o pagamento é registrado"
        def pagamento = service.registrarPagamento(avulso.id, new com.photoizer.crm.financeiro.api.PagamentoRequest(
            new BigDecimal("100.00"), null, null, "Entrada", FormaPagamento.PIX,
            com.photoizer.crm.shared.model.TipoPagamento.ENTRADA))

        then: "tipo e forma são persistidos e o evento é publicado"
        pagamento.formaPagamento == FormaPagamento.PIX
        pagamento.tipoPagamento == com.photoizer.crm.shared.model.TipoPagamento.ENTRADA
        1 * eventPublisher.publishEvent(_)
    }

    def "recebimento manual permite dois pagamentos de mesmo valor"() {
        given: "um avulso confirmado e dois recebimentos iguais"
        def avulso = avulsoPercentual()
        def eventPublisher = Mock(ApplicationEventPublisher)
        def fileStorage = Mock(com.photoizer.crm.shared.storage.FileStorageService)
        def fileValidator = Mock(com.photoizer.crm.shared.storage.FileValidator)
        def service = new PagamentoService(
            pagamentoRepository, agendamentoRepository, eventPublisher, fileStorage, fileValidator)

        agendamentoRepository.findById(avulso.id) >> Optional.of(avulso)

        when: 'dois recebimentos de R$ 100 são registrados'
        service.registrarRecebimento(avulso.id, new BigDecimal("100.00"), LocalDateTime.now(),
            FormaPagamento.PIX, com.photoizer.crm.shared.model.TipoPagamento.ENTRADA, null, "Parcela 1")
        service.registrarRecebimento(avulso.id, new BigDecimal("100.00"), LocalDateTime.now(),
            FormaPagamento.PIX, com.photoizer.crm.shared.model.TipoPagamento.ENTRADA, null, "Parcela 2")

        then: "os dois são persistidos"
        2 * pagamentoRepository.save(_) >> { args -> args[0] }
    }

    private Agendamento avulsoPercentual() {
        agendamento(
            origem: OrigemAgendamento.AVULSO,
            status: StatusAgendamento.CONFIRMADO,
            valorTotal: new BigDecimal("1000.00"),
            valorEntradaExigido: new BigDecimal("300.00"),
            valorEntradaPago: BigDecimal.ZERO,
            valorRestante: new BigDecimal("1000.00"),
            valorTotalFinal: new BigDecimal("1000.00"),
            taxaDeslocamento: BigDecimal.ZERO,
            pacote: pacote(valorBase: new BigDecimal("1000.00")))
    }

    private Agendamento avulsoRecebido() {
        agendamento(
            origem: OrigemAgendamento.AVULSO,
            status: StatusAgendamento.CONFIRMADO,
            valorTotal: new BigDecimal("1000.00"),
            valorEntradaExigido: new BigDecimal("300.00"),
            valorEntradaPago: new BigDecimal("300.00"),
            valorRestante: new BigDecimal("700.00"),
            valorTotalFinal: new BigDecimal("1000.00"),
            taxaDeslocamento: BigDecimal.ZERO,
            percentualEntrada: new BigDecimal("30.00"),
            pacote: pacote(valorBase: new BigDecimal("1000.00")))
    }
}
