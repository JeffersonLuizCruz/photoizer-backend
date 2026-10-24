package com.photoizer.crm.agenda.service

import com.photoizer.crm.agenda.exception.PropostaNaoEncontradaException
import com.photoizer.crm.agenda.exception.PropostaTokenExpiradoException
import com.photoizer.crm.agenda.model.OrigemAgendamento
import com.photoizer.crm.agenda.model.StatusAgendamento
import com.photoizer.crm.agenda.repository.AgendamentoFotografoRepository
import com.photoizer.crm.agenda.repository.AgendamentoRepository
import com.photoizer.crm.agenda.repository.AssinaturaRepository
import com.photoizer.crm.config.service.ConfiguracaoService
import com.photoizer.crm.shared.exception.BadRequestException
import com.photoizer.crm.shared.pdf.PdfDocument
import com.photoizer.crm.shared.pdf.PdfWriter
import com.photoizer.crm.shared.storage.FileStorageService
import com.photoizer.crm.shared.storage.FileValidator
import com.photoizer.crm.shared.storage.StoragePathResolver
import com.photoizer.crm.shared.util.HashUtils
import org.springframework.web.multipart.MultipartFile
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir
import spock.lang.Unroll
import tools.jackson.databind.ObjectMapper

import java.nio.file.Path
import java.time.LocalDateTime

import static com.photoizer.crm.agenda.support.AgendaFixtures.agendamento
import static com.photoizer.crm.agenda.support.AgendaFixtures.cliente

class PropostaPublicaServiceSpec extends Specification {

    @TempDir
    Path tempDir

    def agendamentoRepository = Mock(AgendamentoRepository)
    def agendamentoFotografoRepository = Mock(AgendamentoFotografoRepository)
    def assinaturaRepository = Mock(AssinaturaRepository)
    def agendamentoService = Mock(AgendamentoService)
    def fileStorageService = Mock(FileStorageService)
    def fileValidator = Mock(FileValidator)
    def pdfWriter = Mock(PdfWriter)
    def termoPdfComposer = new TermoPdfComposer()
    def configuracaoService = Mock(ConfiguracaoService)
    def templateService = Mock(PropostaTemplateService)
    def objectMapper = Mock(ObjectMapper)
    def pathResolver = Mock(StoragePathResolver)

    @Subject
    def service = new PropostaPublicaService(
        agendamentoRepository,
        agendamentoFotografoRepository,
        assinaturaRepository,
        agendamentoService,
        fileStorageService,
        fileValidator,
        pdfWriter,
        termoPdfComposer,
        configuracaoService,
        templateService,
        objectMapper,
        pathResolver)

    def "rejeita token de proposta inexistente"() {
        given: "nenhuma proposta corresponde ao token"
        agendamentoRepository.findByTokenPropostaHash(HashUtils.sha256("inexistente")) >> Optional.empty()

        when: "o link público é consultado"
        service.buscarPublico("inexistente")

        then: "uma proposta não encontrada é reportada"
        thrown(PropostaNaoEncontradaException)
    }

    def "rejeita token de proposta expirado"() {
        given: "uma proposta em pré-reserva com token expirado"
        def token = "token-expirado"
        def proposta = agendamento(
            status: StatusAgendamento.PRE_RESERVA,
            tokenPropostaExpiracao: LocalDateTime.now().minusDays(1))
        agendamentoRepository.findByTokenPropostaHash(HashUtils.sha256(token)) >> Optional.of(proposta)
        agendamentoFotografoRepository.findByAgendamentoIdWithFotografo(proposta.id) >> []
        templateService.buildPlaceholders(*_) >> [:]

        when: "o link público é consultado"
        service.status(token)

        then: "o token expirado é reportado"
        thrown(PropostaTokenExpiradoException)
    }

    def "não permite assinar proposta fora da pré-reserva"() {
        given: "uma proposta já aprovada"
        def token = "token-aprovado"
        def proposta = agendamento(
            status: StatusAgendamento.CONFIRMADO,
            tokenPropostaExpiracao: LocalDateTime.now().plusDays(1))
        agendamentoRepository.findByTokenPropostaHash(HashUtils.sha256(token)) >> Optional.of(proposta)
        agendamentoFotografoRepository.findByAgendamentoIdWithFotografo(proposta.id) >> []
        templateService.buildPlaceholders(*_) >> [:]

        when: "o cliente tenta assinar"
        service.assinar(token, "Maria", "11999999999", "m@x.com", "12345678901",
            "São Paulo", "SP", "true", null, null, null, null, null)

        then: "a assinatura é bloqueada"
        thrown(BadRequestException)
    }

    @Unroll
    def "valida campo obrigatório #campo"() {
        given: "uma proposta em pré-reserva válida"
        def token = "token-campos"
        def proposta = propostaValida(token)
        agendamentoRepository.findByTokenPropostaHash(HashUtils.sha256(token)) >> Optional.of(proposta)
        agendamentoFotografoRepository.findByAgendamentoIdWithFotografo(proposta.id) >> []
        templateService.buildPlaceholders(*_) >> [:]

        when: "a assinatura recebe dados incompletos"
        service.assinar(token, nome, telefone, "m@x.com", cpf, "São Paulo", "SP", autoriza,
            Mock(MultipartFile), null, null, null, null)

        then: "a validação bloqueia a assinatura"
        def exception = thrown(BadRequestException)
        exception.message.contains(mensagem)

        where:
        campo       | nome   | telefone       | cpf           | autoriza | mensagem
        "nome"      | ""     | "11999999999"  | "12345678901" | "true"   | "Nome completo"
        "telefone"  | "Maria"| ""             | "12345678901" | "true"   | "Telefone"
        "cpf"       | "Maria"| "11999999999"  | ""            | "true"   | "CPF"
        "imagem"    | "Maria"| "11999999999"  | "12345678901" | ""       | "uso de imagem"
    }

    def "assina proposta válida, gera snapshot e transita para aguardando aprovação"() {
        given: "uma proposta em pré-reserva e um comprovante válido"
        def token = "token-valido"
        def proposta = propostaValida(token)
        def cliente = cliente()
        def comprovante = Mock(MultipartFile)
        comprovante.isEmpty() >> false
        comprovante.getContentType() >> "application/pdf"

        agendamentoRepository.findByTokenPropostaHash(HashUtils.sha256(token)) >> Optional.of(proposta)
        agendamentoFotografoRepository.findByAgendamentoIdWithFotografo(proposta.id) >> []
        templateService.buildPlaceholders(*_) >> [:]
        agendamentoService.resolverCliente(_, _, _, _, _, _) >> cliente
        fileStorageService.salvarEmSubdiretorio(comprovante, proposta.id, "comprovante_entrada") >>
            "uploads/comprovante.pdf"
        fileStorageService.getUploadDir() >> tempDir
        pdfWriter.gerar(_ as PdfDocument) >> "pdf".bytes
        pathResolver.relativo(_) >> "propostas/${proposta.id}.pdf"
        objectMapper.writeValueAsString(_) >> "{}"
        assinaturaRepository.findByAgendamentoId(proposta.id) >> Optional.empty()
        assinaturaRepository.save(_) >> { args -> args[0] }
        agendamentoRepository.save(_) >> { args -> args[0] }

        when: "o cliente assina a proposta"
        def resultado = service.assinar(token, "Maria", "11999999999", "m@x.com", "12345678901",
            "São Paulo", "SP", "true", comprovante, "agent", "web", "America/Sao_Paulo", "127.0.0.1")

        then: "o cliente, o snapshot e o comprovante são registrados"
        resultado.status == StatusAgendamento.AGUARDANDO_APROVACAO
        resultado.cliente == cliente
        resultado.autorizaUsoImagem
        resultado.urlComprovanteEntrada == "uploads/comprovante.pdf"
        resultado.snapshotJson == "{}"
        resultado.snapshotHash != null
        resultado.urlPdfAssinatura == "propostas/${proposta.id}.pdf"

        and: "a assinatura é persistida"
        1 * assinaturaRepository.save(_)
        1 * fileValidator.validate(comprovante, "receipt")

        cleanup: "os arquivos temporários gerados são removidos"
        tempDir.toFile().deleteDir()
    }

    def "assina contrato de agendamento avulso sem comprovante e mantém o status confirmado"() {
        given: "um agendamento avulso confirmado, ainda sem assinatura"
        def token = "token-avulso"
        def proposta = agendamento(
            status: StatusAgendamento.CONFIRMADO,
            origem: OrigemAgendamento.AVULSO,
            tokenProposta: token,
            tokenPropostaExpiracao: LocalDateTime.now().plusDays(1),
            dataAssinatura: null,
            valorEntradaPago: new BigDecimal("300.00"))
        def cliente = cliente()

        agendamentoRepository.findByTokenPropostaHash(HashUtils.sha256(token)) >> Optional.of(proposta)
        agendamentoFotografoRepository.findByAgendamentoIdWithFotografo(proposta.id) >> []
        templateService.buildPlaceholders(*_) >> [:]
        agendamentoService.resolverCliente(_, _, _, _, _, _) >> cliente
        fileStorageService.getUploadDir() >> tempDir
        pdfWriter.gerar(_ as PdfDocument) >> "pdf".bytes
        pathResolver.relativo(_) >> "propostas/${proposta.id}.pdf"
        objectMapper.writeValueAsString(_) >> "{}"
        assinaturaRepository.findByAgendamentoId(proposta.id) >> Optional.empty()
        assinaturaRepository.save(_) >> { args -> args[0] }
        agendamentoRepository.save(_) >> { args -> args[0] }

        when: "o cliente assina sem anexar comprovante"
        def resultado = service.assinar(token, "Maria", "11999999999", "m@x.com", "12345678901",
            "São Paulo", "SP", "true", null, "agent", "web", "America/Sao_Paulo", "127.0.0.1")

        then: "o cliente e o aceite são registrados, sem alterar o status"
        resultado.status == StatusAgendamento.CONFIRMADO
        resultado.cliente == cliente
        resultado.autorizaUsoImagem
        resultado.urlComprovanteEntrada == null
        resultado.dataAssinatura != null
        resultado.snapshotJson == "{}"
        resultado.urlPdfAssinatura == "propostas/${proposta.id}.pdf"

        and: "nenhum comprovante é validado"
        0 * fileValidator.validate(_, _)

        cleanup: "os arquivos temporários gerados são removidos"
        tempDir.toFile().deleteDir()
    }

    def "expõe a entrada já paga e dispensa comprovante na proposta pública"() {
        given: "uma proposta com a entrada integralmente recebida"
        def token = "token-publico-entrada-paga"
        def proposta = agendamento(
            status: StatusAgendamento.PRE_RESERVA,
            tokenProposta: token,
            tokenPropostaExpiracao: LocalDateTime.now().plusDays(1),
            valorEntradaExigido: new BigDecimal("500.00"),
            valorEntradaPago: new BigDecimal("500.00"))

        agendamentoRepository.findByTokenPropostaHash(HashUtils.sha256(token)) >> Optional.of(proposta)
        agendamentoFotografoRepository.findByAgendamentoIdWithFotografo(proposta.id) >> []
        templateService.buildPlaceholders(*_) >> [:]
        agendamentoFotografoRepository.findByAgendamentoIdWithFotografo(proposta.id) >> []
        templateService.buildPlaceholders(*_) >> [:]
        templateService.carregarTemplate() >> null

        when: "o link público é consultado"
        def response = service.buscarPublico(token)

        then: "a entrada recebida é refletida e o comprovante não é exigido"
        response.valorEntradaExigido() == new BigDecimal("500.00")
        response.valorEntradaPago() == new BigDecimal("500.00")
        !response.exigeComprovante()
    }

    def "assina proposta com entrada já paga sem exigir comprovante"() {
        given: "uma proposta em pré-reserva com a entrada integralmente recebida"
        def token = "token-entrada-paga"
        def proposta = agendamento(
            status: StatusAgendamento.PRE_RESERVA,
            tokenProposta: token,
            tokenPropostaExpiracao: LocalDateTime.now().plusDays(1),
            valorEntradaExigido: new BigDecimal("500.00"),
            valorEntradaPago: new BigDecimal("500.00"),
            percentualEntrada: new BigDecimal("25.00"))
        def cliente = cliente()

        agendamentoRepository.findByTokenPropostaHash(HashUtils.sha256(token)) >> Optional.of(proposta)
        agendamentoFotografoRepository.findByAgendamentoIdWithFotografo(proposta.id) >> []
        templateService.buildPlaceholders(*_) >> [:]
        agendamentoService.resolverCliente(_, _, _, _, _, _) >> cliente
        fileStorageService.getUploadDir() >> tempDir
        pdfWriter.gerar(_ as PdfDocument) >> "pdf".bytes
        pathResolver.relativo(_) >> "propostas/${proposta.id}.pdf"
        objectMapper.writeValueAsString(_) >> "{}"
        assinaturaRepository.findByAgendamentoId(proposta.id) >> Optional.empty()
        assinaturaRepository.save(_) >> { args -> args[0] }
        agendamentoRepository.save(_) >> { args -> args[0] }

        when: "o cliente assina sem anexar comprovante"
        def resultado = service.assinar(token, "Maria", "11999999999", "m@x.com", "12345678901",
            "São Paulo", "SP", "true", null, "agent", "web", "America/Sao_Paulo", "127.0.0.1")

        then: "o aceite é registrado sem validar comprovante"
        resultado.status == StatusAgendamento.AGUARDANDO_APROVACAO
        resultado.cliente == cliente
        resultado.urlComprovanteEntrada == null
        0 * fileValidator.validate(_, _)

        cleanup: "os arquivos temporários gerados são removidos"
        tempDir.toFile().deleteDir()
    }

    @Unroll
    def "define status da reserva #esperado para origem #origem e valor pago #valorPago"() {
        expect:
        service.statusReserva(agendamento(origem: origem, valorEntradaPago: valorPago)) == esperado

        where:
        origem                            | valorPago              || esperado
        null                              | BigDecimal.ZERO        || "recebida"
        OrigemAgendamento.PROPOSTA        | BigDecimal.ZERO        || "recebida"
        OrigemAgendamento.AVULSO          | BigDecimal.ZERO        || "a receber"
        OrigemAgendamento.AVULSO          | new BigDecimal("300.00") || "recebida"
    }

    private def propostaValida(String token) {
        agendamento(
            status: StatusAgendamento.PRE_RESERVA,
            tokenProposta: token,
            tokenPropostaExpiracao: LocalDateTime.now().plusDays(1),
            percentualEntrada: new BigDecimal("30.00"))
    }
}
