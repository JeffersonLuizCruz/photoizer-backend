package com.photoizer.crm.agenda.service

import com.photoizer.crm.agenda.event.AgendamentoConfirmadoEvent
import com.photoizer.crm.agenda.event.AgendamentoCriadoEvent
import com.photoizer.crm.agenda.model.StatusAgendamento
import com.photoizer.crm.agenda.repository.AgendamentoRepository
import com.photoizer.crm.auth.repository.UserRepository
import com.photoizer.crm.cliente.repository.ClienteRepository
import com.photoizer.crm.pacote.repository.PacoteRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import spock.lang.Specification
import org.spockframework.spring.SpringBean

import java.time.LocalDateTime

import static com.photoizer.crm.agenda.support.AgendaFixtures.novoFotografo
import static com.photoizer.crm.agenda.support.AgendaFixtures.pacote

@SpringBootTest
@Transactional
@TestPropertySource(properties = [
    "app.storage.upload-dir=target/test-uploads",
    "spring.datasource.url=jdbc:h2:mem:agenda-fluxo;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
])
class AgendamentoPropostaFluxoIntegrationSpec extends Specification {

    @SpringBean
    ApplicationEventPublisher eventPublisher = Mock()

    @Autowired
    AgendamentoService agendamentoService

    @Autowired
    PropostaPublicaService propostaPublicaService

    @Autowired
    AgendamentoRepository agendamentoRepository

    @Autowired
    PacoteRepository pacoteRepository

    @Autowired
    UserRepository userRepository

    @Autowired
    ClienteRepository clienteRepository

    def "executa o fluxo completo de proposta até a aprovação"() {
        given: "um pacote e um fotógrafo cadastrados"
        def pacote = pacoteRepository.save(pacote(id: null))
        def fotografo = userRepository.save(novoFotografo())
        def command = new CriarPropostaCommand(
            pacote.id, null, fotografo.id, LocalDateTime.now().plusDays(20),
            90, "Estúdio", null, null, true, null, null, null, null, null, [])

        when: "o staff cria a proposta"
        def proposta = agendamentoService.criarProposta(command)

        then: "a proposta nasce em pré-reserva com link público"
        proposta.status == StatusAgendamento.PRE_RESERVA
        proposta.tokenProposta
        proposta.tokenPropostaHash
        proposta.tokenPropostaExpiracao != null

        when: "o cliente assina e envia o comprovante"
        def comprovante = new MockMultipartFile(
            "comprovante", "comprovante.pdf", "application/pdf", "%PDF-1.4\n".bytes)
        def assinada = propostaPublicaService.assinar(
            proposta.tokenProposta, "Maria Souza", "11999999999", "maria@teste.com",
            "12345678901", "São Paulo", "SP", "true", comprovante,
            "agent", "web", "America/Sao_Paulo", "127.0.0.1")

        then: "a proposta aguarda aprovação com cliente e comprovante"
        assinada.status == StatusAgendamento.AGUARDANDO_APROVACAO
        assinada.cliente != null
        assinada.urlComprovanteEntrada != null
        assinada.urlPdfAssinatura != null
        assinada.snapshotHash != null

        when: "o staff confirma o pagamento e aprova"
        def paga = agendamentoService.confirmarPagamento(assinada.id)
        def aprovada = agendamentoService.aprovar(paga.id)

        then: "o agendamento é confirmado e os eventos são publicados"
        aprovada.status == StatusAgendamento.CONFIRMADO
        aprovada.dataConfirmacao != null
        1 * eventPublisher.publishEvent(_ as AgendamentoCriadoEvent)
        1 * eventPublisher.publishEvent(_ as AgendamentoConfirmadoEvent)

        and: "o estado é persistido no banco"
        def persistido = agendamentoRepository.findById(aprovada.id).orElseThrow()
        persistido.status == StatusAgendamento.CONFIRMADO
        persistido.cliente.id == assinada.cliente.id

        cleanup: "os arquivos gerados no teste são removidos"
        new File("target/test-uploads").deleteDir()
    }

    def "aprova proposta com entrada já paga sem exigir comprovante nem confirmação manual"() {
        given: "um pacote e um fotógrafo cadastrados"
        def pacote = pacoteRepository.save(pacote(id: null))
        def fotografo = userRepository.save(novoFotografo())
        def command = new CriarPropostaCommand(
            pacote.id, null, fotografo.id, LocalDateTime.now().plusDays(20),
            90, "Estúdio", null, new BigDecimal("300.00"), true, null, null, null, null, null, [])

        when: "o staff cria a proposta com a entrada já paga"
        def proposta = agendamentoService.criarProposta(command)

        then: "a entrada paga substitui o percentual configurado"
        proposta.valorEntradaExigido == new BigDecimal("300.00")
        proposta.valorEntradaPago == new BigDecimal("300.00")
        proposta.valorRestante == new BigDecimal("700.00")

        when: "o cliente assina sem anexar comprovante"
        def assinada = propostaPublicaService.assinar(
            proposta.tokenProposta, "Maria Souza", "11999999999", "maria@teste.com",
            "12345678901", "São Paulo", "SP", "true", null,
            "agent", "web", "America/Sao_Paulo", "127.0.0.1")

        then: "a proposta aguarda aprovação sem comprovante"
        assinada.status == StatusAgendamento.AGUARDANDO_APROVACAO
        assinada.urlComprovanteEntrada == null

        when: "o staff aprova diretamente"
        def aprovada = agendamentoService.aprovar(assinada.id)

        then: "o agendamento é confirmado mantendo a entrada já paga"
        aprovada.status == StatusAgendamento.CONFIRMADO
        aprovada.valorEntradaPago == new BigDecimal("300.00")
        aprovada.valorRestante == new BigDecimal("700.00")

        cleanup: "os arquivos gerados no teste são removidos"
        new File("target/test-uploads").deleteDir()
    }

    def "não permite aprovar proposta com conflito de agenda"() {
        given: "duas propostas para o mesmo fotógrafo no mesmo horário"
        def pacote = pacoteRepository.save(pacote(id: null))
        def fotografo = userRepository.save(novoFotografo())
        def dataHora = LocalDateTime.now().plusDays(30)
        def primeira = agendamentoService.criarProposta(new CriarPropostaCommand(
            pacote.id, null, fotografo.id, dataHora, 60, "Estúdio", null, null, true,
            null, null, null, null, null, []))
        def segunda = agendamentoService.criarProposta(new CriarPropostaCommand(
            pacote.id, null, fotografo.id, dataHora, 60, "Estúdio", null, null, true,
            null, null, null, null, null, []))

        and: "a primeira proposta é assinada, paga e confirmada"
        def comprovante = new MockMultipartFile(
            "comprovante", "comprovante.pdf", "application/pdf", "%PDF-1.4\n".bytes)
        def assinada = propostaPublicaService.assinar(
            primeira.tokenProposta, "Maria", "11999999999", "m@x.com", "12345678901",
            "São Paulo", "SP", "true", comprovante, null, null, null, null)
        agendamentoService.confirmarPagamento(assinada.id)
        agendamentoService.aprovar(assinada.id)

        and: "a segunda proposta também é assinada e paga"
        def segundaAssinada = propostaPublicaService.assinar(
            segunda.tokenProposta, "João", "11888888888", "j@x.com", "98765432100",
            "São Paulo", "SP", "true", comprovante, null, null, null, null)
        agendamentoService.confirmarPagamento(segundaAssinada.id)

        when: "a segunda proposta tenta ser aprovada no mesmo horário"
        agendamentoService.aprovar(segundaAssinada.id)

        then: "o conflito de agenda bloqueia a aprovação"
        thrown(com.photoizer.crm.agenda.exception.ConflitoDeAgendaException)

        cleanup: "os arquivos gerados no teste são removidos"
        new File("target/test-uploads").deleteDir()
    }
}
