package com.photoizer.crm.ecommerce.service

import com.photoizer.crm.agenda.model.Agendamento
import com.photoizer.crm.agenda.model.StatusAgendamento
import com.photoizer.crm.ecommerce.event.CompraExtraFotosAssociadasEvent
import com.photoizer.crm.ecommerce.exception.FotoIndisponivelException
import com.photoizer.crm.ecommerce.model.CompraExtra
import com.photoizer.crm.ecommerce.model.FaseGaleria
import com.photoizer.crm.ecommerce.model.GaleriaEnsaio
import com.photoizer.crm.ecommerce.model.ItemCarrinho
import com.photoizer.crm.ecommerce.model.ModoGaleria
import com.photoizer.crm.ecommerce.model.SelecaoEnsaio
import com.photoizer.crm.ecommerce.model.StatusSelecao
import com.photoizer.crm.ecommerce.repository.CompraExtraRepository
import com.photoizer.crm.foto.model.FotoEnsaio
import com.photoizer.crm.foto.model.StatusFoto
import com.photoizer.crm.foto.repository.FotoEnsaioRepository
import com.photoizer.crm.pacote.model.Pacote
import com.photoizer.crm.shared.storage.FileStorageService
import com.photoizer.crm.shared.storage.FileValidator
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification
import spock.lang.Subject

class EcommerceCheckoutFaseSpec extends Specification {

    def token = UUID.randomUUID()
    def sessionId = UUID.randomUUID()
    def agendamentoId = UUID.randomUUID()
    def fotoId = UUID.randomUUID()

    def fotoRepository = Mock(FotoEnsaioRepository)
    def compraRepository = Mock(CompraExtraRepository)
    def fileStorage = Mock(FileStorageService)
    def eventPublisher = Mock(ApplicationEventPublisher)
    def galeriaQueryService = Mock(GaleriaQueryService)
    def carrinhoService = Mock(CarrinhoService)
    def selecaoFotosService = Mock(SelecaoFotosService)
    def galeriaEnsaioService = Mock(GaleriaEnsaioService)
    def fileValidator = Mock(FileValidator)

    @Subject
    def service = new EcommerceService(
        fotoRepository,
        compraRepository,
        fileStorage,
        eventPublisher,
        galeriaQueryService,
        carrinhoService,
        selecaoFotosService,
        galeriaEnsaioService,
        fileValidator)

    def "permite checkout de extra durante a edição quando ela está na seleção ativa"() {
        given:
        def agendamento = agendamento()
        def foto = foto()
        def selecao = SelecaoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .numero(1)
            .status(StatusSelecao.EM_EDICAO)
            .enviadaEm(java.time.LocalDateTime.now())
            .build()
        galeriaQueryService.buscarAgendamentoPorToken(token) >> agendamento
        galeriaEnsaioService.obter(agendamentoId) >>
            galeria(ModoGaleria.COM_EDICAO, FaseGaleria.EM_EDICAO)
        galeriaQueryService.buscarSelecaoAtiva(agendamentoId) >> Optional.of(selecao)
        carrinhoService.listarCarrinho(token, sessionId) >>
            [ItemCarrinho.builder().agendamentoId(agendamentoId).fotoId(fotoId).sessionId(sessionId).build()]
        fotoRepository.findAllById(_) >> [foto]
        galeriaQueryService.isFotoExtraEmSelecaoAtiva(foto) >> true
        galeriaQueryService.getValorUnitarioExtra(agendamentoId, fotoId) >> new BigDecimal('20.00')
        compraRepository.save(_ as CompraExtra) >> { CompraExtra compra ->
            compra.id = UUID.randomUUID()
            compra
        }

        when:
        def compra = service.checkout(token, sessionId, null)

        then:
        compra.status.name() == 'AGUARDANDO_COMPROVANTE'
        compra.valorTotal == new BigDecimal('20.00')
        1 * eventPublisher.publishEvent({ it instanceof CompraExtraFotosAssociadasEvent })
        1 * carrinhoService.limparCarrinho(token, sessionId)
    }

    def "checkout com comprovante cria compra já aguardando confirmação"() {
        given:
        def agendamento = agendamento()
        def foto = foto()
        def selecao = SelecaoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .numero(1)
            .status(StatusSelecao.EM_EDICAO)
            .enviadaEm(java.time.LocalDateTime.now())
            .build()
        def comprovante = Mock(org.springframework.web.multipart.MultipartFile)
        def compraSalva = null
        galeriaQueryService.buscarAgendamentoPorToken(token) >> agendamento
        galeriaEnsaioService.obter(agendamentoId) >>
            galeria(ModoGaleria.COM_EDICAO, FaseGaleria.EM_EDICAO)
        galeriaQueryService.buscarSelecaoAtiva(agendamentoId) >> Optional.of(selecao)
        carrinhoService.listarCarrinho(token, sessionId) >>
            [ItemCarrinho.builder().agendamentoId(agendamentoId).fotoId(fotoId).sessionId(sessionId).build()]
        fotoRepository.findAllById(_) >> [foto]
        galeriaQueryService.isFotoExtraEmSelecaoAtiva(foto) >> true
        galeriaQueryService.getValorUnitarioExtra(agendamentoId, fotoId) >> new BigDecimal('20.00')
        fileValidator.validate(comprovante, 'receipt') >> {}
        fileStorage.salvarEmSubdiretorio(comprovante, agendamentoId, 'comprovante_extra') >>
            'uploads/comprovante.jpg'
        compraRepository.save(_ as CompraExtra) >> { CompraExtra compra ->
            if (compra.id == null) compra.id = UUID.randomUUID()
            compraSalva = compra
            compra
        }
        compraRepository.findById(_ as UUID) >> { Optional.of(compraSalva) }

        when:
        def compra = service.checkoutComprovante(token, sessionId, comprovante)

        then:
        compra.status.name() == 'AGUARDANDO_CONFIRMACAO'
        compra.urlComprovante == 'uploads/comprovante.jpg'
        1 * eventPublisher.publishEvent({ it instanceof com.photoizer.crm.ecommerce.event.ComprovanteCompraEnviadoEvent })
        0 * eventPublisher.publishEvent({ it instanceof com.photoizer.crm.ecommerce.event.CompraExtraSolicitadaEvent })
        1 * carrinhoService.limparCarrinho(token, sessionId)
    }

    def "comprovante inválido não cria compra"() {
        given:
        def comprovante = Mock(org.springframework.web.multipart.MultipartFile)
        fileValidator.validate(comprovante, 'receipt') >> { throw new IllegalArgumentException('inválido') }

        when:
        service.checkoutComprovante(token, sessionId, comprovante)

        then:
        thrown(IllegalArgumentException)
        0 * compraRepository.save(_)
        0 * carrinhoService.limparCarrinho(_, _)
    }

    def "não permite checkout antes do envio da seleção"() {
        given:
        galeriaQueryService.buscarAgendamentoPorToken(token) >> agendamento()
        galeriaEnsaioService.obter(agendamentoId) >>
            galeria(ModoGaleria.COM_EDICAO, FaseGaleria.SELECAO)

        when:
        service.checkout(token, sessionId, null)

        then:
        thrown(FotoIndisponivelException)
        0 * carrinhoService.listarCarrinho(_, _)
    }

    def "não permite checkout de foto que não está na seleção ativa"() {
        given:
        def foto = foto()
        def selecao = SelecaoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .numero(1)
            .status(StatusSelecao.EM_EDICAO)
            .enviadaEm(java.time.LocalDateTime.now())
            .build()
        galeriaQueryService.buscarAgendamentoPorToken(token) >> agendamento()
        galeriaEnsaioService.obter(agendamentoId) >>
            galeria(ModoGaleria.COM_EDICAO, FaseGaleria.EM_EDICAO)
        galeriaQueryService.buscarSelecaoAtiva(agendamentoId) >> Optional.of(selecao)
        carrinhoService.listarCarrinho(token, sessionId) >>
            [ItemCarrinho.builder().agendamentoId(agendamentoId).fotoId(fotoId).sessionId(sessionId).build()]
        fotoRepository.findAllById(_) >> [foto]
        galeriaQueryService.isFotoExtraEmSelecaoAtiva(foto) >> false

        when:
        service.checkout(token, sessionId, null)

        then:
        thrown(FotoIndisponivelException)
        0 * compraRepository.save(_)
    }

    private Agendamento agendamento() {
        return Agendamento.builder()
            .id(agendamentoId)
            .status(StatusAgendamento.CONFIRMADO)
            .pacote(Pacote.builder().id(UUID.randomUUID()).nome('Ensaio').quantidadeFotos(10).build())
            .build()
    }

    private GaleriaEnsaio galeria(ModoGaleria modo, FaseGaleria fase) {
        return GaleriaEnsaio.builder()
            .agendamentoId(agendamentoId)
            .modo(modo)
            .fase(fase)
            .build()
    }

    private FotoEnsaio foto() {
        return FotoEnsaio.builder()
            .id(fotoId)
            .agendamentoId(agendamentoId)
            .status(StatusFoto.PUBLICADA)
            .visivel(true)
            .selecionadaPacote(false)
            .build()
    }
}
