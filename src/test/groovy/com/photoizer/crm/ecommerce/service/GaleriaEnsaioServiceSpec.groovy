package com.photoizer.crm.ecommerce.service

import com.photoizer.crm.config.service.ConfiguracaoService
import com.photoizer.crm.config.model.ConfigKey
import com.photoizer.crm.ecommerce.exception.GaleriaEmEdicaoException
import com.photoizer.crm.ecommerce.model.FaseGaleria
import com.photoizer.crm.ecommerce.model.GaleriaEnsaio
import com.photoizer.crm.ecommerce.model.ModoGaleria
import com.photoizer.crm.ecommerce.model.StatusSelecao
import com.photoizer.crm.ecommerce.repository.GaleriaEnsaioRepository
import com.photoizer.crm.ecommerce.repository.SelecaoEnsaioItemRepository
import com.photoizer.crm.ecommerce.repository.SelecaoEnsaioRepository
import com.photoizer.crm.foto.model.FotoEnsaio
import com.photoizer.crm.foto.model.StatusFoto
import com.photoizer.crm.foto.model.VersaoFoto
import com.photoizer.crm.foto.repository.FotoEnsaioRepository
import com.photoizer.crm.shared.exception.UnprocessableException
import spock.lang.Specification
import spock.lang.Subject

class GaleriaEnsaioServiceSpec extends Specification {

    def agendamentoId = UUID.randomUUID()
    def galeriaRepository = Mock(GaleriaEnsaioRepository)
    def selecaoRepository = Mock(SelecaoEnsaioRepository)
    def selecaoItemRepository = Mock(SelecaoEnsaioItemRepository)
    def fotoRepository = Mock(FotoEnsaioRepository)
    def configuracaoService = Mock(ConfiguracaoService)

    @Subject
    def service = new GaleriaEnsaioService(
        galeriaRepository, selecaoRepository, selecaoItemRepository, fotoRepository, configuracaoService)

    def "não permite migrar galeria legada depois que a edição foi publicada"() {
        given:
        def galeria = galeria(ModoGaleria.DIRETO, FaseGaleria.COMERCIALIZACAO)
        galeriaRepository.findById(agendamentoId) >> Optional.of(galeria)
        galeriaRepository.findByIdForUpdate(agendamentoId) >> Optional.of(galeria)
        selecaoRepository.existsByAgendamentoIdAndStatusIn(agendamentoId, [StatusSelecao.ENVIADA, StatusSelecao.EM_EDICAO, StatusSelecao.CONCLUIDA]) >> false
        selecaoRepository.existsByAgendamentoIdAndStatusIn(agendamentoId, [StatusSelecao.PUBLICADA]) >> true

        when:
        service.alterarModo(agendamentoId, ModoGaleria.COM_EDICAO)

        then:
        def erro = thrown(GaleriaEmEdicaoException)
        erro.message.contains("publicação")
        0 * galeriaRepository.save(_)
    }

    def "não permite reativar o fluxo direto desativado"() {
        when:
        service.alterarModo(agendamentoId, ModoGaleria.DIRETO)

        then:
        def erro = thrown(UnprocessableException)
        erro.message.contains("desativado")
        0 * galeriaRepository.findByIdForUpdate(_)
    }

    def "galeria sem configuração assume COM_EDICAO na fase de seleção"() {
        given:
        galeriaRepository.findById(agendamentoId) >> Optional.empty()
        configuracaoService.getValor(ConfigKey.GALERIA_MODO_PADRAO) >> "DIRETO"

        when:
        def galeria = service.obter(agendamentoId)

        then:
        galeria.modo == ModoGaleria.COM_EDICAO
        galeria.fase == FaseGaleria.SELECAO
    }

    def "permite ativar seleção e edição em galeria limpa"() {
        given:
        def galeria = galeria(ModoGaleria.DIRETO, FaseGaleria.COMERCIALIZACAO)
        galeriaRepository.findById(agendamentoId) >> Optional.of(galeria)
        galeriaRepository.findByIdForUpdate(agendamentoId) >> Optional.of(galeria)
        selecaoRepository.existsByAgendamentoIdAndStatusIn(agendamentoId, [StatusSelecao.ENVIADA, StatusSelecao.EM_EDICAO, StatusSelecao.CONCLUIDA]) >> false
        selecaoRepository.existsByAgendamentoIdAndStatusIn(agendamentoId, [StatusSelecao.PUBLICADA]) >> false
        fotoRepository.findByAgendamentoIdOrderByOrdemAsc(agendamentoId) >> []
        galeriaRepository.save(galeria) >> galeria

        when:
        service.alterarModo(agendamentoId, ModoGaleria.COM_EDICAO)

        then:
        galeria.modo == ModoGaleria.COM_EDICAO
        galeria.fase == FaseGaleria.SELECAO
    }

    def "permite ativar seleção sem edição em galeria limpa"() {
        given:
        def galeria = galeria(ModoGaleria.COM_EDICAO, FaseGaleria.SELECAO)
        galeriaRepository.findById(agendamentoId) >> Optional.of(galeria)
        galeriaRepository.findByIdForUpdate(agendamentoId) >> Optional.of(galeria)
        selecaoRepository.existsByAgendamentoIdAndStatusIn(agendamentoId, [StatusSelecao.ENVIADA, StatusSelecao.EM_EDICAO, StatusSelecao.CONCLUIDA]) >> false
        selecaoRepository.existsByAgendamentoIdAndStatusIn(agendamentoId, [StatusSelecao.PUBLICADA]) >> false
        fotoRepository.findByAgendamentoIdOrderByOrdemAsc(agendamentoId) >> []
        galeriaRepository.save(galeria) >> galeria

        when:
        service.alterarModo(agendamentoId, ModoGaleria.SEM_EDICAO)

        then:
        galeria.modo == ModoGaleria.SEM_EDICAO
        galeria.fase == FaseGaleria.SELECAO
    }

    def "não permite trocar para sem edição quando já existe versão editada"() {
        given:
        def galeria = galeria(ModoGaleria.COM_EDICAO, FaseGaleria.SELECAO)
        galeriaRepository.findById(agendamentoId) >> Optional.of(galeria)
        galeriaRepository.findByIdForUpdate(agendamentoId) >> Optional.of(galeria)
        selecaoRepository.existsByAgendamentoIdAndStatusIn(agendamentoId, [StatusSelecao.ENVIADA, StatusSelecao.EM_EDICAO, StatusSelecao.CONCLUIDA]) >> false
        selecaoRepository.existsByAgendamentoIdAndStatusIn(agendamentoId, [StatusSelecao.PUBLICADA]) >> false
        fotoRepository.findByAgendamentoIdOrderByOrdemAsc(agendamentoId) >>
            [foto(VersaoFoto.EDITADA, "editadas/1.jpg")]

        when:
        service.alterarModo(agendamentoId, ModoGaleria.SEM_EDICAO)

        then:
        def erro = thrown(GaleriaEmEdicaoException)
        erro.message.contains("versões editadas")
        0 * galeriaRepository.save(_)
    }

    def "respeita o modo padrão sem edição da configuração"() {
        given:
        galeriaRepository.findById(agendamentoId) >> Optional.empty()
        configuracaoService.getValor(ConfigKey.GALERIA_MODO_PADRAO) >> "SEM_EDICAO"

        when:
        def galeria = service.obter(agendamentoId)

        then:
        galeria.modo == ModoGaleria.SEM_EDICAO
        galeria.fase == FaseGaleria.SELECAO
    }

    def "podeAlterarModo é falso quando já existe versão editada"() {
        given:
        selecaoRepository.existsByAgendamentoIdAndStatusIn(agendamentoId, [StatusSelecao.ENVIADA, StatusSelecao.EM_EDICAO, StatusSelecao.CONCLUIDA]) >> false
        selecaoRepository.existsByAgendamentoIdAndStatusIn(agendamentoId, [StatusSelecao.PUBLICADA]) >> false
        fotoRepository.findByAgendamentoIdOrderByOrdemAsc(agendamentoId) >>
            [foto(VersaoFoto.EDITADA, "editadas/1.jpg")]

        expect:
        !service.podeAlterarModo(agendamentoId)
    }


    def "podeAlterarModo é falso quando existe seleção publicada"() {
        given:
        selecaoRepository.existsByAgendamentoIdAndStatusIn(agendamentoId, [StatusSelecao.ENVIADA, StatusSelecao.EM_EDICAO, StatusSelecao.CONCLUIDA]) >> false
        selecaoRepository.existsByAgendamentoIdAndStatusIn(agendamentoId, [StatusSelecao.PUBLICADA]) >> true

        expect:
        !service.podeAlterarModo(agendamentoId)
    }

    def "podeAlterarModo é verdadeiro em galeria sem seleção, venda ou download"() {
        given:
        selecaoRepository.existsByAgendamentoIdAndStatusIn(agendamentoId, [StatusSelecao.ENVIADA, StatusSelecao.EM_EDICAO, StatusSelecao.CONCLUIDA]) >> false
        selecaoRepository.existsByAgendamentoIdAndStatusIn(agendamentoId, [StatusSelecao.PUBLICADA]) >> false
        fotoRepository.findByAgendamentoIdOrderByOrdemAsc(agendamentoId) >> []

        expect:
        service.podeAlterarModo(agendamentoId)
    }

    def "pode reabrir seleção enquanto não houver download ou venda"() {
        given:
        fotoRepository.findByAgendamentoIdOrderByOrdemAsc(agendamentoId) >>
            [foto(VersaoFoto.ORIGINAL, null)]

        expect:
        service.podeReabrirSelecao(agendamentoId)
    }

    def "não pode reabrir seleção após download das fotos"() {
        given:
        def foto = foto(VersaoFoto.ORIGINAL, null)
        foto.dataDownload = java.time.LocalDateTime.now()
        fotoRepository.findByAgendamentoIdOrderByOrdemAsc(agendamentoId) >> [foto]

        expect:
        !service.podeReabrirSelecao(agendamentoId)
    }

    def "define capa publicada e congela a versão original"() {
        given:
        def galeria = galeria(ModoGaleria.COM_EDICAO, FaseGaleria.SELECAO)
        def foto = foto(VersaoFoto.ORIGINAL, null)
        galeriaRepository.findById(agendamentoId) >> Optional.of(galeria)
        galeriaRepository.findByIdForUpdate(agendamentoId) >> Optional.of(galeria)
        fotoRepository.findById(foto.id) >> Optional.of(foto)
        galeriaRepository.save(galeria) >> galeria

        when:
        def salva = service.definirCapa(agendamentoId, foto.id)

        then:
        salva.capaFotoId == foto.id
        salva.capaVersao == VersaoFoto.ORIGINAL
    }

    def "congela a versão editada quando a foto já foi publicada"() {
        given:
        def galeria = galeria(ModoGaleria.COM_EDICAO, FaseGaleria.SELECAO)
        def foto = foto(VersaoFoto.EDITADA, "editadas/1.jpg")
        galeriaRepository.findById(agendamentoId) >> Optional.of(galeria)
        galeriaRepository.findByIdForUpdate(agendamentoId) >> Optional.of(galeria)
        fotoRepository.findById(foto.id) >> Optional.of(foto)
        galeriaRepository.save(galeria) >> galeria

        when:
        def salva = service.definirCapa(agendamentoId, foto.id)

        then:
        salva.capaVersao == VersaoFoto.EDITADA
    }

    def "remove a capa manual"() {
        given:
        def galeria = galeria(ModoGaleria.COM_EDICAO, FaseGaleria.SELECAO)
        galeria.capaFotoId = UUID.randomUUID()
        galeria.capaVersao = VersaoFoto.ORIGINAL
        galeriaRepository.findById(agendamentoId) >> Optional.of(galeria)
        galeriaRepository.findByIdForUpdate(agendamentoId) >> Optional.of(galeria)
        galeriaRepository.save(galeria) >> galeria

        when:
        def salva = service.definirCapa(agendamentoId, null)

        then:
        salva.capaFotoId == null
        salva.capaVersao == null
    }

    def "rejeita capa que não está publicada"() {
        given:
        def galeria = galeria(ModoGaleria.COM_EDICAO, FaseGaleria.SELECAO)
        def foto = foto(VersaoFoto.ORIGINAL, null)
        foto.status = StatusFoto.INEDITA
        galeriaRepository.findById(agendamentoId) >> Optional.of(galeria)
        galeriaRepository.findByIdForUpdate(agendamentoId) >> Optional.of(galeria)
        fotoRepository.findById(foto.id) >> Optional.of(foto)

        when:
        service.definirCapa(agendamentoId, foto.id)

        then:
        thrown(com.photoizer.crm.ecommerce.exception.FotoIndisponivelException)
        0 * galeriaRepository.save(_)
    }

    def "rejeita capa fora da seleção publicada em comercialização"() {
        given:
        def galeria = galeria(ModoGaleria.COM_EDICAO, FaseGaleria.COMERCIALIZACAO)
        def foto = foto(VersaoFoto.EDITADA, "editadas/1.jpg")
        def selecao = com.photoizer.crm.ecommerce.model.SelecaoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .numero(1)
            .status(StatusSelecao.PUBLICADA)
            .enviadaEm(java.time.LocalDateTime.now())
            .build()
        galeriaRepository.findById(agendamentoId) >> Optional.of(galeria)
        galeriaRepository.findByIdForUpdate(agendamentoId) >> Optional.of(galeria)
        fotoRepository.findById(foto.id) >> Optional.of(foto)
        selecaoRepository.findFirstByAgendamentoIdAndStatusInOrderByNumeroDesc(
            agendamentoId, [StatusSelecao.PUBLICADA]) >> Optional.of(selecao)
        selecaoItemRepository.findBySelecaoIdAndFotoId(selecao.id, foto.id) >> Optional.empty()

        when:
        service.definirCapa(agendamentoId, foto.id)

        then:
        thrown(com.photoizer.crm.ecommerce.exception.FotoIndisponivelException)
        0 * galeriaRepository.save(_)
    }

    private FotoEnsaio foto(VersaoFoto versao, String editadaPath) {
        return FotoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .fileName("IMG_1.jpg")
            .originalPath("orig/1.jpg")
            .watermarkedPath("wm/1.jpg")
            .thumbPath("thumb/1.jpg")
            .versaoAtual(versao)
            .editadaPath(editadaPath)
            .status(StatusFoto.PUBLICADA)
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
}
