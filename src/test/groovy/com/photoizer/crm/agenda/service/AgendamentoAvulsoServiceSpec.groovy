package com.photoizer.crm.agenda.service

import com.photoizer.crm.agenda.api.AgendamentoMapper
import com.photoizer.crm.agenda.api.OpcoesFotografoResponse
import com.photoizer.crm.agenda.model.Agendamento
import com.photoizer.crm.agenda.model.OrigemAgendamento
import com.photoizer.crm.agenda.model.StatusAgendamento
import com.photoizer.crm.agenda.repository.AgendamentoRepository
import com.photoizer.crm.auth.repository.UserRepository
import com.photoizer.crm.config.model.ConfigKey
import com.photoizer.crm.config.service.ConfiguracaoService
import com.photoizer.crm.financeiro.service.PagamentoManualCommand
import com.photoizer.crm.financeiro.service.PagamentoService
import com.photoizer.crm.pacote.service.PacoteQueryService
import com.photoizer.crm.shared.exception.BadRequestException
import com.photoizer.crm.shared.model.FormaPagamento
import com.photoizer.crm.shared.model.TipoPagamento
import spock.lang.Specification
import spock.lang.Subject

import java.time.LocalDateTime

import static com.photoizer.crm.agenda.support.AgendaFixtures.fotografo
import static com.photoizer.crm.agenda.support.AgendaFixtures.pacote

class AgendamentoAvulsoServiceSpec extends Specification {

    def pacoteQueryService = Mock(PacoteQueryService)
    def userRepository = Mock(UserRepository)
    def agendamentoRepository = Mock(AgendamentoRepository)
    def disponibilidadeService = Mock(DisponibilidadeService)
    def configuracaoService = Mock(ConfiguracaoService)
    def pagamentoService = Mock(PagamentoService)
    def agendamentoMapper = Mock(AgendamentoMapper)
    def agendamentoService = Mock(AgendamentoService)
    def fotografoOpcoesService = Mock(FotografoOpcoesService)

    @Subject
    def service = new AgendamentoAvulsoService(
        pacoteQueryService,
        userRepository,
        agendamentoRepository,
        disponibilidadeService,
        configuracaoService,
        pagamentoService,
        agendamentoMapper,
        agendamentoService,
        fotografoOpcoesService)

    def pacote
    def fotografo

    def setup() {
        pacote = pacote(valorBase: new BigDecimal("1000.00"), ativo: true)
        fotografo = fotografo()

        pacoteQueryService.buscarEntityPorId(pacote.id) >> pacote
        userRepository.findById(fotografo.id) >> Optional.of(fotografo)
        configuracaoService.getValorDecimal(ConfigKey.TAXA_DESLOCAMENTO) >> new BigDecimal("50.00")
        configuracaoService.getValorDecimal(ConfigKey.PERCENTUAL_ENTRADA) >> new BigDecimal("30.00")
        configuracaoService.getValorInteiro(ConfigKey.CONTRATO_DIAS_VALIDADE) >> 7
        agendamentoRepository.save(_) >> { args ->
            def agendamento = (Agendamento) args[0]
            if (agendamento.id == null) {
                agendamento.id = UUID.randomUUID()
            }
            agendamento
        }
    }

    def "cria agendamento avulso confirmado com percentual de entrada e token público"() {
        given: "um comando com percentual de entrada sobre o pacote mais deslocamento"
        def command = command(percentualEntrada: new BigDecimal("20.00"))

        when: "o agendamento avulso é criado"
        def agendamento = service.criar(command)

        then: "o agendamento nasce confirmado, sem cliente e com link público"
        agendamento.status == StatusAgendamento.CONFIRMADO
        agendamento.origem == OrigemAgendamento.AVULSO
        agendamento.cliente == null
        agendamento.fotografo == fotografo
        agendamento.valorTotal == new BigDecimal("1050.00")
        agendamento.valorEntradaExigido == new BigDecimal("210.00")
        agendamento.valorEntradaPago == BigDecimal.ZERO
        agendamento.valorRestante == new BigDecimal("1050.00")
        agendamento.percentualEntrada == new BigDecimal("20.00")
        agendamento.tokenProposta != null
        agendamento.tokenPropostaHash != null
        agendamento.tokenPropostaExpiracao != null
        agendamento.dataEnvioProposta != null
        agendamento.tokenGaleria != null
        agendamento.dataConfirmacao != null
        !agendamento.autorizaUsoImagem
        0 * pagamentoService.registrarPagamentosHistoricos(_, _)
    }

    def "cria agendamento avulso com valor já recebido e registra o pagamento"() {
        given: "um comando com o valor da reserva já recebido"
        def command = command(valorJaRecebido: new BigDecimal("300.00"))

        when: "o agendamento avulso é criado"
        def agendamento = service.criar(command)

        then: "a entrada exigida passa a ser o valor recebido"
        agendamento.status == StatusAgendamento.CONFIRMADO
        agendamento.valorTotal == new BigDecimal("1050.00")
        agendamento.valorEntradaExigido == new BigDecimal("300.00")
        agendamento.valorEntradaPago == new BigDecimal("300.00")
        agendamento.valorRestante == new BigDecimal("750.00")
        agendamento.percentualEntrada == new BigDecimal("28.57")

        and: "o recebimento é registrado no financeiro sem alterar o status"
        1 * pagamentoService.registrarPagamentosHistoricos(_, { List<PagamentoManualCommand> pagamentos ->
            pagamentos.size() == 1
                && pagamentos.first().valor() == new BigDecimal("300.00")
                && pagamentos.first().tipoPagamento() == TipoPagamento.ENTRADA
                && pagamentos.first().formaPagamento() == FormaPagamento.PIX
        })
    }

    def "usa o percentual configurado quando nenhum modo é informado"() {
        given: "um comando sem percentual e sem valor recebido"
        def command = command()

        when: "o agendamento avulso é criado"
        def agendamento = service.criar(command)

        then: "o percentual padrão da configuração é aplicado"
        agendamento.valorEntradaExigido == new BigDecimal("315.00")
        agendamento.valorEntradaPago == BigDecimal.ZERO
        agendamento.percentualEntrada == new BigDecimal("30.00")
        !agendamento.valoresPersonalizados
    }

    def "rejeita comando com percentual e valor recebido ao mesmo tempo"() {
        given: "um comando com os dois modos preenchidos"
        def command = command(percentualEntrada: new BigDecimal("30.00"), valorJaRecebido: new BigDecimal("300.00"))

        when: "o agendamento avulso é criado"
        service.criar(command)

        then: "a criação é bloqueada"
        def exception = thrown(BadRequestException)
        exception.message.contains("percentual")
        0 * agendamentoRepository.save(_)
    }

    def "rejeita valor já recebido maior que o total"() {
        given: "um comando com recebimento acima do total"
        def command = command(valorJaRecebido: new BigDecimal("1050.01"))

        when: "o agendamento avulso é criado"
        service.criar(command)

        then: "a criação é bloqueada"
        def exception = thrown(BadRequestException)
        exception.message.contains("valor já recebido")
        0 * agendamentoRepository.save(_)
    }

    def "registra ensaio no passado como confirmado sem validar conflito de agenda"() {
        given: "um comando com data no passado"
        def command = command(dataHoraEnsaio: LocalDateTime.now().minusDays(1))

        when: "o agendamento avulso é criado"
        def agendamento = service.criar(command)

        then: "o ensaio é registrado como confirmado e o horário histórico não ocupa a agenda"
        agendamento.status == StatusAgendamento.CONFIRMADO
        agendamento.origem == OrigemAgendamento.AVULSO
        0 * disponibilidadeService.validarConflitoAgenda(_, _, _, _, _)
    }

    def "valida conflito de agenda ao criar ensaio avulso futuro"() {
        given: "um comando com data futura"
        def command = command()

        when: "o agendamento avulso é criado"
        service.criar(command)

        then: "o conflito é validado normalmente"
        1 * disponibilidadeService.validarConflitoAgenda(_, _, _, _, _)
    }

    def "rejeita comando sem data do ensaio"() {
        given: "um comando sem data"
        def command = command(dataHoraEnsaio: null)

        when: "o agendamento avulso é criado"
        service.criar(command)

        then: "a criação é bloqueada antes de salvar"
        def exception = thrown(BadRequestException)
        exception.message.contains("data e hora")
        0 * agendamentoRepository.save(_)
    }

    def "rejeita responsável que não é fotógrafo"() {
        given: "um usuário com papel AGENDADOR"
        def agendador = com.photoizer.crm.agenda.support.AgendaFixtures.usuario(
            "Agendador", com.photoizer.crm.auth.model.Papel.AGENDADOR)
        userRepository.findById(agendador.id) >> Optional.of(agendador)
        def command = command(fotografoId: agendador.id)

        when: "o agendamento avulso é criado"
        service.criar(command)

        then: "o papel é validado"
        def exception = thrown(BadRequestException)
        exception.message.contains("não é um fotógrafo")
    }

    def "opcoes expõe fotógrafos ativos e o responsável padrão resolvido"() {
        given: "o serviço de opções já resolveu o responsável padrão"
        def padraoId = UUID.randomUUID()
        fotografoOpcoesService.opcoes(_) >> new OpcoesFotografoResponse(
            [new OpcoesFotografoResponse.FotografoOpcao(fotografo.id, fotografo.nome)], padraoId)

        when: "as opções do avulso são consultadas"
        def opcoes = service.opcoes(UUID.randomUUID())

        then: "a lista e o padrão são repassados, junto dos valores configurados"
        opcoes.fotografos.size() == 1
        opcoes.fotografos.first().id == fotografo.id
        opcoes.fotografoPadraoId == padraoId
        opcoes.percentualEntrada == new BigDecimal("30.00")
        opcoes.taxaDeslocamentoPadrao == new BigDecimal("50.00")
    }

    private CriarAgendamentoAvulsoCommand command(Map<String, Object> overrides = [:]) {
        def valores = [
            pacoteId            : pacote.id,
            fotografoId         : fotografo.id,
            dataHoraEnsaio      : LocalDateTime.now().plusDays(10),
            duracaoMinutos      : 60,
            localEnsaio         : "Praia",
            percentualEntrada   : null,
            valorJaRecebido     : null,
            dataRecebimento     : LocalDateTime.now(),
            formaPagamento      : FormaPagamento.PIX,
            custoDeslocamento   : new BigDecimal("50.00"),
            repassarDeslocamento: true,
            observacoes         : "Avulso",
        ]
        valores.putAll(overrides)
        new CriarAgendamentoAvulsoCommand(
            (UUID) valores.pacoteId,
            (UUID) valores.fotografoId,
            (LocalDateTime) valores.dataHoraEnsaio,
            (Integer) valores.duracaoMinutos,
            (String) valores.localEnsaio,
            (BigDecimal) valores.percentualEntrada,
            (BigDecimal) valores.valorJaRecebido,
            (LocalDateTime) valores.dataRecebimento,
            (FormaPagamento) valores.formaPagamento,
            (BigDecimal) valores.custoDeslocamento,
            (Boolean) valores.repassarDeslocamento,
            (String) valores.observacoes)
    }
}
