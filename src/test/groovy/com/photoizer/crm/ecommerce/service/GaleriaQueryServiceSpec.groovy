package com.photoizer.crm.ecommerce.service

import com.photoizer.crm.agenda.model.Agendamento
import com.photoizer.crm.agenda.repository.AgendamentoRepository
import com.photoizer.crm.config.service.ConfiguracaoService
import com.photoizer.crm.ecommerce.model.FaseGaleria
import com.photoizer.crm.ecommerce.model.GaleriaEnsaio
import com.photoizer.crm.ecommerce.model.ModoGaleria
import com.photoizer.crm.ecommerce.model.SelecaoEnsaio
import com.photoizer.crm.ecommerce.model.SelecaoEnsaioItem
import com.photoizer.crm.ecommerce.model.StatusSelecao
import com.photoizer.crm.ecommerce.model.TipoItemSelecao
import com.photoizer.crm.ecommerce.exception.GaleriaArquivadaException
import com.photoizer.crm.ecommerce.repository.SelecaoEnsaioItemRepository
import com.photoizer.crm.ecommerce.repository.SelecaoEnsaioRepository
import com.photoizer.crm.foto.model.FotoEnsaio
import com.photoizer.crm.foto.model.StatusFoto
import com.photoizer.crm.foto.model.VersaoFoto
import com.photoizer.crm.foto.repository.FotoEnsaioRepository
import spock.lang.Specification
import spock.lang.Subject

class GaleriaQueryServiceSpec extends Specification {

    def agendamentoId = UUID.randomUUID()
    def token = UUID.randomUUID()
    def agendamentoRepository = Mock(AgendamentoRepository)
    def fotoRepository = Mock(FotoEnsaioRepository)
    def configuracaoService = Mock(ConfiguracaoService)
    def galeriaEnsaioService = Mock(GaleriaEnsaioService)
    def selecaoRepository = Mock(SelecaoEnsaioRepository)
    def selecaoItemRepository = Mock(SelecaoEnsaioItemRepository)

    @Subject
    def service = new GaleriaQueryService(
        agendamentoRepository,
        fotoRepository,
        configuracaoService,
        galeriaEnsaioService,
        selecaoRepository,
        selecaoItemRepository)

    def "download do pacote em galeria sem edição usa o original após aprovação"() {
        given:
        def foto = foto(VersaoFoto.ORIGINAL)
        foto.selecionadaPacote = true
        galeriaEnsaioService.obter(agendamentoId) >>
            galeria(ModoGaleria.SEM_EDICAO, FaseGaleria.COMERCIALIZACAO)

        expect:
        service.isDownloadPermitido(foto)
    }

    def "extra em galeria sem edição só é baixável depois de confirmado o pagamento"() {
        given:
        def publicada = foto(VersaoFoto.ORIGINAL)
        def paga = foto(VersaoFoto.ORIGINAL)
        paga.status = StatusFoto.PAGA
        galeriaEnsaioService.obter(agendamentoId) >>
            galeria(ModoGaleria.SEM_EDICAO, FaseGaleria.COMERCIALIZACAO)

        expect:
        !service.isDownloadPermitido(publicada)
        service.isDownloadPermitido(paga)
    }

    def "nada é baixável antes da aprovação em galeria sem edição"() {
        given:
        def foto = foto(VersaoFoto.ORIGINAL)
        foto.selecionadaPacote = true
        galeriaEnsaioService.obter(agendamentoId) >>
            galeria(ModoGaleria.SEM_EDICAO, FaseGaleria.SELECAO_ENVIADA)

        expect:
        !service.isDownloadPermitido(foto)
    }

    def "foto da seleção publicada em galeria sem edição é comercializável em original"() {
        given:
        def foto = foto(VersaoFoto.ORIGINAL)
        galeriaEnsaioService.obter(agendamentoId) >>
            galeria(ModoGaleria.SEM_EDICAO, FaseGaleria.COMERCIALIZACAO)

        expect:
        service.isFotoComercializavel(foto)
    }

    def "foto restante fora da seleção publicada é comercializável com edição sob demanda"() {
        given:
        def foto = foto(VersaoFoto.ORIGINAL)
        galeriaEnsaioService.obter(agendamentoId) >>
            galeria(ModoGaleria.COM_EDICAO, FaseGaleria.COMERCIALIZACAO)
        selecaoRepository.findFirstByAgendamentoIdAndStatusInOrderByNumeroDesc(
            agendamentoId, [StatusSelecao.PUBLICADA]) >> Optional.empty()

        expect:
        service.isFotoComercializavel(foto)
    }

    def "foto da seleção publicada só é comercializável na versão editada"() {
        given:
        def selecao = selecaoPublicada()
        def editada = foto(VersaoFoto.EDITADA)
        def original = foto(VersaoFoto.ORIGINAL)
        galeriaEnsaioService.obter(agendamentoId) >>
            galeria(ModoGaleria.COM_EDICAO, FaseGaleria.COMERCIALIZACAO)
        selecaoRepository.findFirstByAgendamentoIdAndStatusInOrderByNumeroDesc(
            agendamentoId, [StatusSelecao.PUBLICADA]) >> Optional.of(selecao)
        selecaoItemRepository.findBySelecaoIdAndFotoId(selecao.id, editada.id) >>
            Optional.of(item(selecao.id, editada.id))
        selecaoItemRepository.findBySelecaoIdAndFotoId(selecao.id, original.id) >>
            Optional.of(item(selecao.id, original.id))

        expect:
        service.isFotoComercializavel(editada)
        !service.isFotoComercializavel(original)
    }

    def "carrinho fica disponível durante a edição para anexar extras"() {
        given:
        def foto = foto(VersaoFoto.ORIGINAL)
        galeriaEnsaioService.obter(agendamentoId) >>
            galeria(ModoGaleria.COM_EDICAO, FaseGaleria.EM_EDICAO)

        expect:
        service.isFotoDisponivelParaCarrinho(foto)
    }

    def "galeria em comercialização lista também as fotos restantes"() {
        given:
        def restante = foto(VersaoFoto.ORIGINAL)
        agendamentoRepository.findByTokenGaleria(token) >> Optional.of(agendamento())
        galeriaEnsaioService.obter(agendamentoId) >>
            galeria(ModoGaleria.COM_EDICAO, FaseGaleria.COMERCIALIZACAO)
        fotoRepository.findPublicadasVisiveisByAgendamentoId(agendamentoId) >> [restante]

        when:
        def fotos = service.listarFotosPublicadas(token)

        then:
        fotos == [restante]
    }

    def "preço do extra usa a seleção ativa antes da publicação"() {
        given:
        def selecao = SelecaoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .numero(1)
            .status(StatusSelecao.EM_EDICAO)
            .enviadaEm(java.time.LocalDateTime.now())
            .build()
        def foto = foto(VersaoFoto.ORIGINAL)
        selecaoRepository.findFirstByAgendamentoIdAndStatusInOrderByNumeroDesc(
            agendamentoId, [StatusSelecao.ENVIADA, StatusSelecao.EM_EDICAO,
                            StatusSelecao.CONCLUIDA, StatusSelecao.PUBLICADA]) >> Optional.of(selecao)
        selecaoItemRepository.findBySelecaoIdAndFotoId(selecao.id, foto.id) >>
            Optional.of(SelecaoEnsaioItem.builder()
                .selecaoId(selecao.id)
                .fotoId(foto.id)
                .tipo(TipoItemSelecao.EXTRA)
                .valorUnitario(new BigDecimal("37.50"))
                .build())

        expect:
        service.getValorUnitarioExtra(agendamentoId, foto.id) == new BigDecimal("37.50")
    }

    def "galeria com todas as fotos arquivadas responde 410 pelo token"() {
        given:
        agendamentoRepository.findByTokenGaleria(token) >> Optional.of(agendamento())
        fotoRepository.countByAgendamentoId(agendamentoId) >> 3
        fotoRepository.countByAgendamentoIdAndArquivosRemovidosTrue(agendamentoId) >> 3

        when:
        service.buscarAgendamentoPorToken(token)

        then:
        thrown(GaleriaArquivadaException)
    }

    def "galeria parcialmente arquivada continua acessível"() {
        given:
        agendamentoRepository.findByTokenGaleria(token) >> Optional.of(agendamento())
        fotoRepository.countByAgendamentoId(agendamentoId) >> 3
        fotoRepository.countByAgendamentoIdAndArquivosRemovidosTrue(agendamentoId) >> 1

        expect:
        service.buscarAgendamentoPorToken(token) != null
    }

    def "galeria sem fotos não é considerada arquivada"() {
        given:
        fotoRepository.countByAgendamentoId(agendamentoId) >> 0

        expect:
        !service.isGaleriaArquivada(agendamentoId)
    }

    private FotoEnsaio foto(VersaoFoto versao) {        return FotoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .fileName("IMG_1.jpg")
            .originalPath("orig/1.jpg")
            .watermarkedPath("wm/1.jpg")
            .thumbPath("thumb/1.jpg")
            .numeroGaleria(1)
            .status(StatusFoto.PUBLICADA)
            .versaoAtual(versao)
            .visivel(true)
            .build()
    }

    private GaleriaEnsaio galeria(ModoGaleria modo, FaseGaleria fase) {
        return GaleriaEnsaio.builder()
            .agendamentoId(agendamentoId)
            .modo(modo)
            .fase(fase)
            .build()
    }

    private SelecaoEnsaio selecaoPublicada() {
        return SelecaoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .numero(1)
            .status(StatusSelecao.PUBLICADA)
            .enviadaEm(java.time.LocalDateTime.now())
            .build()
    }

    private SelecaoEnsaioItem item(UUID selecaoId, UUID fotoId) {
        return SelecaoEnsaioItem.builder()
            .id(UUID.randomUUID())
            .selecaoId(selecaoId)
            .fotoId(fotoId)
            .numeroGaleria(1)
            .tipo(TipoItemSelecao.EXTRA)
            .ordem(0)
            .build()
    }

    private Agendamento agendamento() {
        return Agendamento.builder()
            .id(agendamentoId)
            .tokenGaleria(token)
            .build()
    }
}
