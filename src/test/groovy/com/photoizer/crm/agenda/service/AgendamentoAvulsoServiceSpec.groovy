package com.photoizer.crm.agenda.service

import com.photoizer.crm.agenda.api.AgendamentoMapper
import com.photoizer.crm.agenda.exception.AgendamentoNoPassadoException
import com.photoizer.crm.agenda.model.Agendamento
import com.photoizer.crm.agenda.model.OrigemAgendamento
import com.photoizer.crm.agenda.model.StatusAgendamento
import com.photoizer.crm.agenda.repository.AgendamentoRepository
import com.photoizer.crm.auth.repository.UserRepository
import com.photoizer.crm.cliente.repository.ClienteRepository
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

import static com.photoizer.crm.agenda.support.AgendaFixtures.cliente
import static com.photoizer.crm.agenda.support.AgendaFixtures.fotografo
import static com.photoizer.crm.agenda.support.AgendaFixtures.pacote

class AgendamentoAvulsoServiceSpec extends Specification {

    def clienteRepository = Mock(ClienteRepository)
    def pacoteQueryService = Mock(PacoteQueryService)
    def userRepository = Mock(UserRepository)
    def agendamentoRepository = Mock(AgendamentoRepository)
    def disponibilidadeService = Mock(DisponibilidadeService)
    def configuracaoService = Mock(ConfiguracaoService)
    def pagamentoService = Mock(PagamentoService)
    def agendamentoMapper = Mock(AgendamentoMapper)

    @Subject
    def service = new AgendamentoAvulsoService(
        clienteRepository,
        pacoteQueryService,
        userRepository,
        agendamentoRepository,
        disponibilidadeService,
        configuracaoService,
        pagamentoService,
        agendamentoMapper)

    def pacote
    def fotografo
    def cliente

    def setup() {
        pacote = pacote(valorBase: new BigDecimal("1000.00"), ativo: true)
        fotografo = fotografo()
        cliente = cliente()

        pacoteQueryService.buscarEntityPorId(pacote.id) >> pacote
        userRepository.findById(fotografo.id) >> Optional.of(fotografo)
        clienteRepository.findByTelefone("(11) 99999-9999") >> Optional.of(cliente)
        configuracaoService.getValorDecimal(ConfigKey.TAXA_DESLOCAMENTO) >> new BigDecimal("50.00")
        configuracaoService.getValorDecimal(ConfigKey.PERCENTUAL_ENTRADA) >> new BigDecimal("30.00")
        agendamentoRepository.save(_) >> { args ->
            def agendamento = (Agendamento) args[0]
            if (agendamento.id == null) {
                agendamento.id = UUID.randomUUID()
            }
            agendamento
        }
    }

    def "cria agendamento avulso confirmado com cliente existente e pagamento inicial"() {
        given: "um comando com valores negociados e pagamento de entrada"
        def command = command(new BigDecimal("1200.00"), new BigDecimal("400.00"), new BigDecimal("200.00"))

        when: "o agendamento avulso é criado"
        def agendamento = service.criar(command)

        then: "o agendamento nasce confirmado, com origem avulsa e pagamento registrado"
        agendamento.status == StatusAgendamento.CONFIRMADO
        agendamento.origem == OrigemAgendamento.AVULSO
        agendamento.cliente == cliente
        agendamento.fotografo == fotografo
        agendamento.valorTotal == new BigDecimal("1200.00")
        agendamento.valorEntradaExigido == new BigDecimal("400.00")
        agendamento.valorEntradaPago == new BigDecimal("200.00")
        agendamento.valorRestante == new BigDecimal("1000.00")
        agendamento.valoresPersonalizados
        agendamento.tokenProposta == null
        agendamento.tokenGaleria != null
        agendamento.dataConfirmacao != null
        1 * pagamentoService.registrarPagamentosHistoricos(_, { List<PagamentoManualCommand> pagamentos ->
            pagamentos.size() == 1
                && pagamentos.first().valor() == new BigDecimal("200.00")
                && pagamentos.first().tipoPagamento() == TipoPagamento.ENTRADA
                && pagamentos.first().formaPagamento() == FormaPagamento.PIX
        })
    }

    def "usa valores padrão do pacote e da configuração quando não informados"() {
        given: "um comando sem valores negociados"
        def command = command(null, null, null)

        when: "o agendamento avulso é criado"
        def agendamento = service.criar(command)

        then: "os valores padrão do pacote e da configuração são aplicados"
        agendamento.valorTotal == new BigDecimal("1050.00")
        agendamento.valorEntradaExigido == new BigDecimal("315.00")
        agendamento.valorEntradaPago == BigDecimal.ZERO
        agendamento.valorRestante == new BigDecimal("1050.00")
        !agendamento.valoresPersonalizados
        0 * pagamentoService.registrarPagamentosHistoricos(_, _)
    }

    def "rejeita data passada"() {
        given: "um comando com data no passado"
        def command = command(null, null, null, LocalDateTime.now().minusDays(1))

        when: "o agendamento avulso é criado"
        service.criar(command)

        then: "a criação é bloqueada antes de salvar"
        thrown(AgendamentoNoPassadoException)
        0 * agendamentoRepository.save(_)
    }

    def "rejeita pagamento maior que o total"() {
        given: "um comando com pagamento acima do valor total"
        def command = command(new BigDecimal("1000.00"), new BigDecimal("300.00"), new BigDecimal("1000.01"))

        when: "o agendamento avulso é criado"
        service.criar(command)

        then: "a criação é bloqueada"
        def exception = thrown(BadRequestException)
        exception.message.contains("valor pago")
        0 * agendamentoRepository.save(_)
    }

    def "rejeita responsável que não é fotógrafo"() {
        given: "um usuário com papel AGENDADOR"
        def agendador = com.photoizer.crm.agenda.support.AgendaFixtures.usuario(
            "Agendador", com.photoizer.crm.auth.model.Papel.AGENDADOR)
        userRepository.findById(agendador.id) >> Optional.of(agendador)
        def command = command(null, null, null, LocalDateTime.now().plusDays(10), agendador.id)

        when: "o agendamento avulso é criado"
        service.criar(command)

        then: "o papel é validado"
        def exception = thrown(BadRequestException)
        exception.message.contains("não é um fotógrafo")
    }

    private CriarAgendamentoAvulsoCommand command(BigDecimal valorTotal, BigDecimal entrada, BigDecimal pago,
                                                   LocalDateTime dataHora = LocalDateTime.now().plusDays(10),
                                                   UUID fotografoId = fotografo.id) {
        new CriarAgendamentoAvulsoCommand(
            "Maria Souza", "11999999999", "maria@teste.com", null,
            "São Paulo", "SP", pacote.id, fotografoId, dataHora, 60, "Praia",
            valorTotal, entrada, pago, LocalDateTime.now(), FormaPagamento.PIX, TipoPagamento.ENTRADA,
            new BigDecimal("50.00"), true, false, "Avulso")
    }
}
