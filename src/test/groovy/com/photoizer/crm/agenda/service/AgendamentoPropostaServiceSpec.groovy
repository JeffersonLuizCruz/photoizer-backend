package com.photoizer.crm.agenda.service

import com.photoizer.crm.agenda.api.AgendamentoMapper
import com.photoizer.crm.agenda.api.AtualizarAgendamentoRequest
import com.photoizer.crm.agenda.event.AgendamentoCanceladoEvent
import com.photoizer.crm.agenda.exception.PropostaNaoEditavelException
import com.photoizer.crm.agenda.exception.PropostaNaoRemovivelException
import com.photoizer.crm.agenda.model.OrigemAgendamento
import com.photoizer.crm.agenda.model.StatusAgendamento
import com.photoizer.crm.agenda.repository.AgendamentoFotografoRepository
import com.photoizer.crm.agenda.repository.AgendamentoRepository
import com.photoizer.crm.agenda.repository.ReatribuicaoAgendamentoRepository
import com.photoizer.crm.auth.model.Papel
import com.photoizer.crm.auth.repository.UserRepository
import com.photoizer.crm.cliente.repository.ClienteRepository
import com.photoizer.crm.config.model.ConfigKey
import com.photoizer.crm.config.service.ConfiguracaoService
import com.photoizer.crm.foto.repository.FotoEnsaioRepository
import com.photoizer.crm.pacote.service.PacoteQueryService
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification
import spock.lang.Subject

import java.time.LocalDateTime

import static com.photoizer.crm.agenda.support.AgendaFixtures.agendamento
import static com.photoizer.crm.agenda.support.AgendaFixtures.fotografo
import static com.photoizer.crm.agenda.support.AgendaFixtures.pacote
import static com.photoizer.crm.agenda.support.AgendaFixtures.usuario

class AgendamentoPropostaServiceSpec extends Specification {

    def clienteRepository = Mock(ClienteRepository)
    def pacoteQueryService = Mock(PacoteQueryService)
    def userRepository = Mock(UserRepository)
    def agendamentoRepository = Mock(AgendamentoRepository)
    def eventPublisher = Mock(ApplicationEventPublisher)
    def fotoEnsaioRepository = Mock(FotoEnsaioRepository)
    def configuracaoService = Mock(ConfiguracaoService)
    def agendamentoFotografoRepository = Mock(AgendamentoFotografoRepository)
    def reatribuicaoAgendamentoRepository = Mock(ReatribuicaoAgendamentoRepository)
    def disponibilidadeService = Mock(DisponibilidadeService)
    def partilhaService = Mock(PartilhaService)
    def agendamentoMapper = Mock(AgendamentoMapper)

    @Subject
    def service = new AgendamentoService(
        clienteRepository,
        pacoteQueryService,
        userRepository,
        agendamentoRepository,
        eventPublisher,
        fotoEnsaioRepository,
        configuracaoService,
        agendamentoFotografoRepository,
        reatribuicaoAgendamentoRepository,
        disponibilidadeService,
        partilhaService,
        new AgendamentoValoresCalculator(),
        agendamentoMapper)

    def "altera proposta em PRE_RESERVA recalculando valores e preservando o token"() {
        given: "uma proposta em pré-reserva"
        def agendamento = agendamento(status: StatusAgendamento.PRE_RESERVA)
        def tokenOriginal = agendamento.tokenProposta
        def pacote = pacote(valorBase: new BigDecimal("2000.00"))
        def fotografo = fotografo()

        agendamentoRepository.findByIdWithLock(agendamento.id) >> Optional.of(agendamento)
        agendamentoRepository.save(_) >> { args -> args[0] }
        pacoteQueryService.buscarEntityPorId(pacote.id) >> pacote
        userRepository.findById(fotografo.id) >> Optional.of(fotografo)
        configuracaoService.getValorDecimal(ConfigKey.TAXA_DESLOCAMENTO) >> new BigDecimal("100.00")
        configuracaoService.getValorDecimal(ConfigKey.PERCENTUAL_ENTRADA) >> new BigDecimal("30.00")
        agendamentoFotografoRepository.findByAgendamentoId(agendamento.id) >> []
        agendamentoFotografoRepository.findByAgendamentoIdWithFotografo(agendamento.id) >> []

        def command = new CriarPropostaCommand(
            pacote.id, null, fotografo.id, LocalDateTime.now().plusDays(5),
            120, "Praia", null, null, false, "Cláusula X", "Obs Y", null, null, null, [])

        when: "a proposta é alterada"
        service.atualizarProposta(agendamento.id, command)

        then: "os valores são recalculados e o token é preservado"
        agendamento.status == StatusAgendamento.PRE_RESERVA
        agendamento.valorTotal == new BigDecimal("2000.00")
        agendamento.valorEntradaExigido == new BigDecimal("600.00")
        agendamento.duracaoMinutos == 120
        agendamento.clausulasPersonalizadas == "Cláusula X"
        agendamento.localEnsaio == "Praia"
        agendamento.tokenProposta == tokenOriginal
        1 * partilhaService.calcularPartilhaFotografo(agendamento)
        1 * agendamentoRepository.save(agendamento) >> agendamento
    }

    def "cria proposta com pacote de R\$ 0,01 usando entrada mínima"() {
        given: "um pacote de um centavo e fotógrafo válido"
        def pacote = pacote(valorBase: new BigDecimal("0.01"))
        def fotografo = fotografo()

        pacoteQueryService.buscarEntityPorId(pacote.id) >> pacote
        userRepository.findById(fotografo.id) >> Optional.of(fotografo)
        configuracaoService.getValorDecimal(ConfigKey.TAXA_DESLOCAMENTO) >> BigDecimal.ZERO
        configuracaoService.getValorDecimal(ConfigKey.PERCENTUAL_ENTRADA) >> new BigDecimal("30.00")
        configuracaoService.getValorInteiro(ConfigKey.CONTRATO_DIAS_VALIDADE) >> 7
        agendamentoRepository.save(_) >> { args -> args[0] }

        def command = new CriarPropostaCommand(
            pacote.id, null, fotografo.id, LocalDateTime.now().plusDays(5),
            360, "Estúdio", null, null, true, null, null, null, null, null, [])

        when: "a proposta é criada"
        def criada = service.criarProposta(command)

        then: "a entrada mínima é aplicada e o token público é gerado"
        criada.status == StatusAgendamento.PRE_RESERVA
        criada.valorTotal == new BigDecimal("0.01")
        criada.valorEntradaExigido == new BigDecimal("0.01")
        criada.valorEntradaPago == BigDecimal.ZERO
        criada.valorRestante == new BigDecimal("0.01")
        criada.tokenProposta
        criada.tokenPropostaHash
    }

    def "cria proposta registrando entrada já paga e ignorando o percentual configurado"() {
        given: "um pacote de R\$ 2.000,00 e entrada já paga de R\$ 500,00"
        def pacote = pacote(valorBase: new BigDecimal("2000.00"))
        def fotografo = fotografo()

        pacoteQueryService.buscarEntityPorId(pacote.id) >> pacote
        userRepository.findById(fotografo.id) >> Optional.of(fotografo)
        configuracaoService.getValorDecimal(ConfigKey.TAXA_DESLOCAMENTO) >> BigDecimal.ZERO
        configuracaoService.getValorDecimal(ConfigKey.PERCENTUAL_ENTRADA) >> new BigDecimal("30.00")
        configuracaoService.getValorInteiro(ConfigKey.CONTRATO_DIAS_VALIDADE) >> 7
        agendamentoRepository.save(_) >> { args -> args[0] }

        def command = new CriarPropostaCommand(
            pacote.id, null, fotografo.id, LocalDateTime.now().plusDays(5),
            360, "Estúdio", null, new BigDecimal("500.00"), true, null, null, null, null, null, [])

        when: "a proposta é criada"
        def criada = service.criarProposta(command)

        then: "a entrada paga substitui os 30%"
        criada.valorEntradaExigido == new BigDecimal("500.00")
        criada.valorEntradaPago == new BigDecimal("500.00")
        criada.valorRestante == new BigDecimal("1500.00")
        criada.percentualEntrada == new BigDecimal("25.00")
    }

    def "altera proposta preservando a entrada já paga informada"() {
        given: "uma proposta em pré-reserva sem entrada paga"
        def agendamento = agendamento(status: StatusAgendamento.PRE_RESERVA)
        def pacote = pacote(valorBase: new BigDecimal("2000.00"))
        def fotografo = fotografo()

        agendamentoRepository.findByIdWithLock(agendamento.id) >> Optional.of(agendamento)
        agendamentoRepository.save(_) >> { args -> args[0] }
        pacoteQueryService.buscarEntityPorId(pacote.id) >> pacote
        userRepository.findById(fotografo.id) >> Optional.of(fotografo)
        configuracaoService.getValorDecimal(ConfigKey.TAXA_DESLOCAMENTO) >> BigDecimal.ZERO
        configuracaoService.getValorDecimal(ConfigKey.PERCENTUAL_ENTRADA) >> new BigDecimal("30.00")
        agendamentoFotografoRepository.findByAgendamentoId(agendamento.id) >> []
        agendamentoFotografoRepository.findByAgendamentoIdWithFotografo(agendamento.id) >> []

        def command = new CriarPropostaCommand(
            pacote.id, null, fotografo.id, LocalDateTime.now().plusDays(5),
            120, "Praia", null, new BigDecimal("400.00"), false, null, null, null, null, null, [])

        when: "a proposta é alterada com entrada já paga"
        service.atualizarProposta(agendamento.id, command)

        then: "a entrada paga e o saldo são recalculados"
        agendamento.valorEntradaExigido == new BigDecimal("400.00")
        agendamento.valorEntradaPago == new BigDecimal("400.00")
        agendamento.valorRestante == new BigDecimal("1600.00")
        agendamento.percentualEntrada == new BigDecimal("20.00")
    }

    def "volta ao percentual configurado quando a entrada já paga é zerada na edição"() {
        given: "uma proposta com entrada já paga"
        def agendamento = agendamento(
            status: StatusAgendamento.PRE_RESERVA,
            valorEntradaExigido: new BigDecimal("500.00"),
            valorEntradaPago: new BigDecimal("500.00"),
            valorRestante: new BigDecimal("1500.00"),
            valorTotal: new BigDecimal("2000.00"),
            valorTotalFinal: new BigDecimal("2000.00"),
            percentualEntrada: new BigDecimal("25.00"))
        def pacote = pacote(valorBase: new BigDecimal("2000.00"))
        def fotografo = fotografo()

        agendamentoRepository.findByIdWithLock(agendamento.id) >> Optional.of(agendamento)
        agendamentoRepository.save(_) >> { args -> args[0] }
        pacoteQueryService.buscarEntityPorId(pacote.id) >> pacote
        userRepository.findById(fotografo.id) >> Optional.of(fotografo)
        configuracaoService.getValorDecimal(ConfigKey.TAXA_DESLOCAMENTO) >> BigDecimal.ZERO
        configuracaoService.getValorDecimal(ConfigKey.PERCENTUAL_ENTRADA) >> new BigDecimal("30.00")
        agendamentoFotografoRepository.findByAgendamentoId(agendamento.id) >> []
        agendamentoFotografoRepository.findByAgendamentoIdWithFotografo(agendamento.id) >> []

        def command = new CriarPropostaCommand(
            pacote.id, null, fotografo.id, LocalDateTime.now().plusDays(5),
            120, "Praia", null, BigDecimal.ZERO, false, null, null, null, null, null, [])

        when: "a proposta é alterada com a entrada zerada"
        service.atualizarProposta(agendamento.id, command)

        then: "o percentual configurado volta a valer e nada permanece pago"
        agendamento.valorEntradaExigido == new BigDecimal("600.00")
        agendamento.valorEntradaPago == BigDecimal.ZERO
        agendamento.valorRestante == new BigDecimal("2000.00")
        agendamento.percentualEntrada == new BigDecimal("30.00")
    }

    def "bloqueia alteração de proposta já assinada"() {
        given: "uma proposta aguardando aprovação"
        def agendamento = agendamento(status: StatusAgendamento.AGUARDANDO_APROVACAO)
        agendamentoRepository.findByIdWithLock(agendamento.id) >> Optional.of(agendamento)

        when: "tenta alterar a proposta"
        service.atualizarProposta(agendamento.id, command())

        then: "a operação é bloqueada antes de consultar o pacote"
        thrown(PropostaNaoEditavelException)
        0 * pacoteQueryService._
    }

    def "remove proposta em análise com soft-cancel auditável e publica evento"() {
        given: "uma proposta em pré-reserva"
        def agendamento = agendamento(status: StatusAgendamento.PRE_RESERVA)
        agendamentoRepository.findByIdWithLock(agendamento.id) >> Optional.of(agendamento)
        agendamentoRepository.save(_) >> { args -> args[0] }

        when: "a proposta é removida"
        service.removerProposta(agendamento.id, "Duplicada", null)

        then: "o agendamento é cancelado com motivo e sem autor"
        agendamento.status == StatusAgendamento.CANCELADO
        agendamento.motivoRecusa == "Duplicada"
        agendamento.dataRecusa != null
        agendamento.recusadoPor == null
        1 * eventPublisher.publishEvent(_ as AgendamentoCanceladoEvent)
    }

    def "usa motivo padrão e registra autor da remoção"() {
        given: "uma proposta em pré-reserva e um administrador"
        def agendamento = agendamento(status: StatusAgendamento.PRE_RESERVA)
        def autor = usuario("Admin", Papel.ADMIN)
        agendamentoRepository.findByIdWithLock(agendamento.id) >> Optional.of(agendamento)
        agendamentoRepository.save(_) >> { args -> args[0] }
        userRepository.findById(autor.id) >> Optional.of(autor)

        when: "a proposta é removida com motivo em branco"
        service.removerProposta(agendamento.id, "  ", autor.id)

        then: "o motivo padrão e o autor são registrados"
        agendamento.status == StatusAgendamento.CANCELADO
        agendamento.motivoRecusa == "Proposta removida pelo staff"
        agendamento.recusadoPor == "Admin"
    }

    def "atualiza avulso preservando total e entrada personalizados"() {
        given: "um agendamento avulso com valores personalizados"
        def agendamento = agendamento(
            status: StatusAgendamento.CONFIRMADO,
            origem: OrigemAgendamento.AVULSO,
            valoresPersonalizados: true,
            valorTotal: new BigDecimal("1500.00"),
            valorEntradaExigido: new BigDecimal("500.00"),
            valorEntradaPago: new BigDecimal("200.00"),
            valorTotalFinal: new BigDecimal("1500.00"),
            valorRestante: new BigDecimal("1300.00"))
        def pacote = pacote(valorBase: new BigDecimal("2000.00"))
        def fotografo = fotografo()

        agendamentoRepository.findById(agendamento.id) >> Optional.of(agendamento)
        agendamentoRepository.save(_) >> { args -> args[0] }
        pacoteQueryService.buscarEntityPorId(pacote.id) >> pacote
        userRepository.findById(fotografo.id) >> Optional.of(fotografo)
        configuracaoService.getValorDecimal("taxaDeslocamentoPadrao", BigDecimal.ZERO) >> new BigDecimal("100.00")
        agendamentoFotografoRepository.findByAgendamentoId(agendamento.id) >> []
        agendamentoFotografoRepository.findByAgendamentoIdWithFotografo(agendamento.id) >> []

        def request = new AtualizarAgendamentoRequest(
            pacote.id, LocalDateTime.now().plusDays(10), "Praia", null, fotografo.id,
            new BigDecimal("100.00"), true, true, "Atualizado", [])

        when: "o agendamento é atualizado"
        service.atualizar(agendamento.id, request)

        then: "os valores personalizados são preservados"
        agendamento.valorTotal == new BigDecimal("1500.00")
        agendamento.valorEntradaExigido == new BigDecimal("500.00")
        agendamento.valorTotalFinal == new BigDecimal("1500.00")
        agendamento.valorRestante == new BigDecimal("1300.00")
    }

    def "bloqueia remoção de agendamento que não é proposta"() {
        given: "um agendamento confirmado"
        def agendamento = agendamento(status: StatusAgendamento.CONFIRMADO)
        agendamentoRepository.findByIdWithLock(agendamento.id) >> Optional.of(agendamento)

        when: "tenta remover a proposta"
        service.removerProposta(agendamento.id, "x", null)

        then: "a remoção é bloqueada e nenhum evento é publicado"
        thrown(PropostaNaoRemovivelException)
        0 * eventPublisher.publishEvent(_)
    }

    private CriarPropostaCommand command() {
        new CriarPropostaCommand(
            UUID.randomUUID(), null, UUID.randomUUID(), LocalDateTime.now().plusDays(5),
            60, "Estúdio", null, null, true, null, null, null, null, null, [])
    }
}
