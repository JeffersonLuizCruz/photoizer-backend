package com.photoizer.crm.ecommerce.service

import com.photoizer.crm.agenda.model.Agendamento
import com.photoizer.crm.ecommerce.event.EdicaoPublicadaEvent
import com.photoizer.crm.ecommerce.event.SelecaoCanceladaEvent
import com.photoizer.crm.ecommerce.event.SelecaoEnviadaEvent
import com.photoizer.crm.ecommerce.event.SelecaoExtraAdicionadaEvent
import com.photoizer.crm.ecommerce.event.SelecaoExtraRemovidaEvent
import com.photoizer.crm.ecommerce.exception.FotoIndisponivelException
import com.photoizer.crm.ecommerce.exception.FotosEditadasIncompletasException
import com.photoizer.crm.ecommerce.exception.SelecaoNaoEditavelException
import com.photoizer.crm.ecommerce.model.FaseGaleria
import com.photoizer.crm.ecommerce.model.GaleriaEnsaio
import com.photoizer.crm.ecommerce.model.ModoGaleria
import com.photoizer.crm.ecommerce.model.SelecaoEnsaio
import com.photoizer.crm.ecommerce.model.SelecaoEnsaioItem
import com.photoizer.crm.ecommerce.model.StatusSelecao
import com.photoizer.crm.ecommerce.model.TipoItemSelecao
import com.photoizer.crm.ecommerce.repository.SelecaoEnsaioItemRepository
import com.photoizer.crm.ecommerce.repository.SelecaoEnsaioRepository
import com.photoizer.crm.foto.api.FotoMapper
import com.photoizer.crm.foto.model.FotoEnsaio
import com.photoizer.crm.foto.model.StatusFoto
import com.photoizer.crm.foto.repository.FotoEnsaioRepository
import com.photoizer.crm.pacote.model.Pacote
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification
import spock.lang.Subject

import java.time.LocalDateTime

class SelecaoEnsaioServiceSpec extends Specification {

    def token = UUID.randomUUID()
    def agendamentoId = UUID.randomUUID()
    def selecaoRepository = Mock(SelecaoEnsaioRepository)
    def itemRepository = Mock(SelecaoEnsaioItemRepository)
    def galeriaService = Mock(GaleriaEnsaioService)
    def galeriaQueryService = Mock(GaleriaQueryService)
    def carrinhoService = Mock(CarrinhoService)
    def selecaoFotosService = Mock(SelecaoFotosService)
    def selecaoFotoValidator = Mock(SelecaoFotoValidator)
    def fotoRepository = Mock(FotoEnsaioRepository)
    def fotoMapper = Mock(FotoMapper)
    def eventPublisher = Mock(ApplicationEventPublisher)

    @Subject
    def service = new SelecaoEnsaioService(
        selecaoRepository,
        itemRepository,
        galeriaService,
        galeriaQueryService,
        carrinhoService,
        selecaoFotosService,
        selecaoFotoValidator,
        fotoRepository,
        fotoMapper,
        eventPublisher
    )

    def "envia a seleção do pacote criando snapshot com número e nome"() {
        given:
        def fotos = [foto(1), foto(2)]
        def agendamento = agendamento(2)
        def galeria = galeria()
        galeriaQueryService.buscarAgendamentoPorToken(token) >> agendamento
        galeriaService.obterParaAtualizacao(agendamentoId) >> galeria
        selecaoRepository.existsByAgendamentoIdAndStatusIn(agendamentoId, _) >> false
        fotoRepository.findByAgendamentoIdOrderByOrdemAsc(agendamentoId) >> fotos
        fotoRepository.findAllById(_ as Iterable) >> fotos
        carrinhoService.listarCarrinho(token, _) >> []
        selecaoRepository.findFirstByAgendamentoIdOrderByNumeroDesc(agendamentoId) >> Optional.empty()
        selecaoRepository.save(_ as SelecaoEnsaio) >> { args ->
            args[0].id = UUID.randomUUID()
            args[0]
        }
        itemRepository.saveAll(_ as Iterable) >> { args -> args[0] as List }

        when:
        def resposta = service.enviar(token, UUID.randomUUID(), fotos*.id, "Seleção do ensaio")

        then:
        resposta.quantidadePacote == 2
        resposta.quantidadeExtras == 0
        resposta.itens*.numeroGaleria == [1, 2]
        resposta.itens*.nomeArquivo == ["IMG_1.jpg", "IMG_2.jpg"]
        galeria.fase == FaseGaleria.SELECAO_ENVIADA
        1 * eventPublisher.publishEvent({ it instanceof SelecaoEnviadaEvent
            && it.agendamentoId() == agendamentoId
            && it.quantidadePacote() == 2 })
    }

    def "rejeita enviar seleção quando a galeria não está em modo de edição"() {
        given:
        galeriaQueryService.buscarAgendamentoPorToken(token) >> agendamento(2)
        galeriaService.obterParaAtualizacao(agendamentoId) >> GaleriaEnsaio.builder()
            .agendamentoId(agendamentoId)
            .modo(ModoGaleria.DIRETO)
            .fase(FaseGaleria.COMERCIALIZACAO)
            .build()

        when:
        service.enviar(token, null, [UUID.randomUUID()], null)

        then:
        thrown(SelecaoNaoEditavelException)
        0 * selecaoRepository.save(_)
    }

    def "não publica edição quando ainda há item sem versão editada"() {
        given:
        def selecao = selecao(StatusSelecao.CONCLUIDA)
        def itens = [item(1, false), item(2, true)]
        galeriaService.obterParaAtualizacao(agendamentoId) >> galeria()
        selecaoRepository.findByIdForUpdate(selecao.id) >> Optional.of(selecao)
        itemRepository.findBySelecaoIdOrderByNumeroGaleriaAsc(selecao.id) >> itens

        when:
        service.publicar(agendamentoId, selecao.id)

        then:
        def erro = thrown(FotosEditadasIncompletasException)
        erro.message.contains("1 foto")
        0 * eventPublisher.publishEvent(_)
    }

    def "publica edição quando todos os itens estão editados"() {
        given:
        def selecao = selecao(StatusSelecao.CONCLUIDA)
        def itens = [item(1, true), item(2, true)]
        def fotos = [foto(1), foto(2)]
        selecaoRepository.findByIdForUpdate(selecao.id) >> Optional.of(selecao)
        itemRepository.findBySelecaoIdOrderByNumeroGaleriaAsc(selecao.id) >> itens
        galeriaService.obterParaAtualizacao(agendamentoId) >> galeria()
        galeriaService.obter(agendamentoId) >> galeria()
        selecaoRepository.save(_ as SelecaoEnsaio) >> { args -> args[0] }
        fotoRepository.findAllById(_ as Iterable) >> fotos

        when:
        def resposta = service.publicar(agendamentoId, selecao.id)

        then:
        resposta.status == 'PUBLICADA'
        !resposta.podeReabrir
        1 * eventPublisher.publishEvent({ it instanceof EdicaoPublicadaEvent
            && it.agendamentoId() == agendamentoId
            && it.fotoIds().size() == 2 })
    }

    def "aprova seleção sem edição e libera a comercialização"() {
        given:
        def selecao = selecao(StatusSelecao.ENVIADA)
        def itens = [item(1, false), item(2, false)]
        def fotos = [foto(1), foto(2)]
        def galeria = galeria(ModoGaleria.SEM_EDICAO)
        galeriaService.obterParaAtualizacao(agendamentoId) >> galeria
        galeriaService.obter(agendamentoId) >> galeria
        galeriaService.podeReabrirSelecao(agendamentoId) >> true
        selecaoRepository.findByIdForUpdate(selecao.id) >> Optional.of(selecao)
        itemRepository.findBySelecaoIdOrderByNumeroGaleriaAsc(selecao.id) >> itens
        selecaoRepository.save(_ as SelecaoEnsaio) >> { args -> args[0] }
        fotoRepository.findAllById(_ as Iterable) >> fotos

        when:
        def resposta = service.publicar(agendamentoId, selecao.id)

        then:
        resposta.status == 'PUBLICADA'
        resposta.quantidadePacote == 2
        resposta.podeReabrir
        1 * eventPublisher.publishEvent({ it instanceof com.photoizer.crm.ecommerce.event.SelecaoAprovadaEvent
            && it.agendamentoId() == agendamentoId
            && it.fotoIds().size() == 2 })
        0 * eventPublisher.publishEvent({ it instanceof EdicaoPublicadaEvent })
    }

    def "aprovação sem edição não exige itens editados"() {
        given:
        def selecao = selecao(StatusSelecao.ENVIADA)
        def itens = [item(1, false)]
        def galeria = galeria(ModoGaleria.SEM_EDICAO)
        galeriaService.obterParaAtualizacao(agendamentoId) >> galeria
        galeriaService.obter(agendamentoId) >> galeria
        galeriaService.podeReabrirSelecao(agendamentoId) >> true
        selecaoRepository.findByIdForUpdate(selecao.id) >> Optional.of(selecao)
        itemRepository.findBySelecaoIdOrderByNumeroGaleriaAsc(selecao.id) >> itens
        selecaoRepository.save(_ as SelecaoEnsaio) >> { args -> args[0] }
        fotoRepository.findAllById(_ as Iterable) >> [foto(1)]

        when:
        service.publicar(agendamentoId, selecao.id)

        then:
        noExceptionThrown()
        selecao.status == StatusSelecao.PUBLICADA
        selecao.publicadaEm != null
        itens.every { !it.editada }
    }

    def "bloqueia início de edição em galeria sem edição"() {
        given:
        galeriaService.obterParaAtualizacao(agendamentoId) >> galeria(ModoGaleria.SEM_EDICAO)
        def selecao = selecao(StatusSelecao.ENVIADA)
        selecaoRepository.findById(selecao.id) >> Optional.of(selecao)

        when:
        service.iniciar(agendamentoId, selecao.id)

        then:
        thrown(SelecaoNaoEditavelException)
        0 * selecaoRepository.save(_)
    }

    def "bloqueia download de originais em galeria sem edição"() {
        given:
        galeriaService.obterParaAtualizacao(agendamentoId) >> galeria(ModoGaleria.SEM_EDICAO)
        def selecao = selecao(StatusSelecao.ENVIADA)
        selecaoRepository.findById(selecao.id) >> Optional.of(selecao)

        when:
        service.registrarDownloadOriginais(agendamentoId, selecao.id)

        then:
        thrown(SelecaoNaoEditavelException)
        0 * selecaoRepository.save(_)
    }

    def "bloqueia reabertura de seleção publicada em galeria com edição"() {
        given:
        def selecao = selecao(StatusSelecao.PUBLICADA)
        galeriaService.obterParaAtualizacao(agendamentoId) >> galeria(ModoGaleria.COM_EDICAO)
        selecaoRepository.findByIdForUpdate(selecao.id) >> Optional.of(selecao)
        itemRepository.findBySelecaoIdOrderByNumeroGaleriaAsc(selecao.id) >> [item(1, true)]

        when:
        service.reabrir(agendamentoId, selecao.id)

        then:
        thrown(SelecaoNaoEditavelException)
        0 * selecaoRepository.save(_)
    }

    def "reabre seleção SEM_EDICAO aprovada sem download ou venda"() {
        given:
        def selecao = selecao(StatusSelecao.PUBLICADA)
        def galeria = galeria(ModoGaleria.SEM_EDICAO)
        galeria.fase = FaseGaleria.COMERCIALIZACAO
        galeriaService.obterParaAtualizacao(agendamentoId) >> galeria
        galeriaService.podeReabrirSelecao(agendamentoId) >> true
        selecaoRepository.findByIdForUpdate(selecao.id) >> Optional.of(selecao)
        itemRepository.findBySelecaoIdOrderByNumeroGaleriaAsc(selecao.id) >> [item(1, false)]
        selecaoRepository.save(_ as SelecaoEnsaio) >> { args -> args[0] }
        fotoRepository.findAllById(_ as Iterable) >> [foto(1)]

        when:
        def resposta = service.reabrir(agendamentoId, selecao.id)

        then:
        resposta.status == 'CANCELADA'
        !resposta.podeReabrir
        galeria.fase == FaseGaleria.SELECAO
        1 * eventPublisher.publishEvent({ it instanceof SelecaoCanceladaEvent
            && it.agendamentoId() == agendamentoId
            && it.fotoIds().size() == 1 })
    }

    def "bloqueia reabertura SEM_EDICAO após download ou venda"() {
        given:
        def selecao = selecao(StatusSelecao.PUBLICADA)
        def galeria = galeria(ModoGaleria.SEM_EDICAO)
        galeria.fase = FaseGaleria.COMERCIALIZACAO
        galeriaService.obterParaAtualizacao(agendamentoId) >> galeria
        galeriaService.podeReabrirSelecao(agendamentoId) >> false
        selecaoRepository.findByIdForUpdate(selecao.id) >> Optional.of(selecao)
        itemRepository.findBySelecaoIdOrderByNumeroGaleriaAsc(selecao.id) >> [item(1, false)]

        when:
        service.reabrir(agendamentoId, selecao.id)

        then:
        def erro = thrown(SelecaoNaoEditavelException)
        erro.message.contains("download ou venda")
        0 * selecaoRepository.save(_)
    }

    def "anexa extra ao snapshot durante a edição com o preço congelado"() {
        given:
        def selecao = selecao(StatusSelecao.EM_EDICAO)
        def foto = foto(3)
        selecaoRepository.findFirstByAgendamentoIdAndStatusInOrderByNumeroDesc(agendamentoId, _) >>
            Optional.of(selecao)
        selecaoRepository.findByIdForUpdate(selecao.id) >> Optional.of(selecao)
        itemRepository.findBySelecaoIdAndFotoId(selecao.id, foto.id) >> Optional.empty()
        galeriaQueryService.buscarAgendamentoPorId(agendamentoId) >> agendamento(2)
        fotoRepository.findById(foto.id) >> Optional.of(foto)
        galeriaQueryService.getValorUnitarioFotoExtra(agendamentoId) >> new BigDecimal("20.00")

        when:
        service.incluirExtra(agendamentoId, foto.id)

        then:
        1 * itemRepository.save({ SelecaoEnsaioItem item ->
            item.selecaoId == selecao.id
                && item.fotoId == foto.id
                && item.tipo == TipoItemSelecao.EXTRA
                && item.valorUnitario == new BigDecimal("20.00")
                && !item.editada
        }) >> { args -> args[0] }
        1 * eventPublisher.publishEvent({ it instanceof SelecaoExtraAdicionadaEvent
            && it.agendamentoId() == agendamentoId
            && it.selecaoId() == selecao.id
            && it.fotoId() == foto.id })
        0 * selecaoRepository.save(_)
    }

    def "volta a seleção concluída para em edição ao anexar um extra"() {
        given:
        def selecao = selecao(StatusSelecao.CONCLUIDA)
        def foto = foto(3)
        selecaoRepository.findFirstByAgendamentoIdAndStatusInOrderByNumeroDesc(agendamentoId, _) >>
            Optional.of(selecao)
        selecaoRepository.findByIdForUpdate(selecao.id) >> Optional.of(selecao)
        itemRepository.findBySelecaoIdAndFotoId(selecao.id, foto.id) >> Optional.empty()
        galeriaQueryService.buscarAgendamentoPorId(agendamentoId) >> agendamento(2)
        fotoRepository.findById(foto.id) >> Optional.of(foto)
        galeriaQueryService.getValorUnitarioFotoExtra(agendamentoId) >> new BigDecimal("20.00")
        itemRepository.save(_ as SelecaoEnsaioItem) >> { args -> args[0] }
        selecaoRepository.save(_ as SelecaoEnsaio) >> { args -> args[0] }

        when:
        service.incluirExtra(agendamentoId, foto.id)

        then:
        selecao.status == StatusSelecao.EM_EDICAO
        selecao.concluidaEm == null
        1 * selecaoRepository.save(selecao)
    }

    def "não anexa extra quando não há seleção ativa"() {
        given:
        selecaoRepository.findFirstByAgendamentoIdAndStatusInOrderByNumeroDesc(agendamentoId, _) >>
            Optional.empty()

        when:
        service.incluirExtra(agendamentoId, UUID.randomUUID())

        then:
        0 * itemRepository.save(_)
        0 * eventPublisher.publishEvent(_)
    }

    def "rejeita extra que já faz parte do pacote"() {
        given:
        def selecao = selecao(StatusSelecao.EM_EDICAO)
        def foto = foto(1)
        foto.selecionadaPacote = true
        selecaoRepository.findFirstByAgendamentoIdAndStatusInOrderByNumeroDesc(agendamentoId, _) >>
            Optional.of(selecao)
        selecaoRepository.findByIdForUpdate(selecao.id) >> Optional.of(selecao)
        itemRepository.findBySelecaoIdAndFotoId(selecao.id, foto.id) >> Optional.empty()
        galeriaQueryService.buscarAgendamentoPorId(agendamentoId) >> agendamento(2)
        fotoRepository.findById(foto.id) >> Optional.of(foto)

        when:
        service.incluirExtra(agendamentoId, foto.id)

        then:
        thrown(FotoIndisponivelException)
        0 * itemRepository.save(_)
    }

    def "remove do snapshot o extra ainda não editado"() {
        given:
        def selecao = selecao(StatusSelecao.EM_EDICAO)
        def item = itemExtra(selecao.id, false)
        selecaoRepository.findFirstByAgendamentoIdAndStatusInOrderByNumeroDesc(agendamentoId, _) >>
            Optional.of(selecao)
        selecaoRepository.findByIdForUpdate(selecao.id) >> Optional.of(selecao)
        itemRepository.findBySelecaoIdAndFotoId(selecao.id, item.fotoId) >> Optional.of(item)
        itemRepository.countBySelecaoId(selecao.id) >> 2
        itemRepository.countBySelecaoIdAndEditadaTrue(selecao.id) >> 1

        when:
        service.removerExtra(agendamentoId, item.fotoId)

        then:
        1 * itemRepository.delete(item)
        1 * eventPublisher.publishEvent({ it instanceof SelecaoExtraRemovidaEvent
            && it.agendamentoId() == agendamentoId
            && it.fotoId() == item.fotoId })
        0 * selecaoRepository.save(_)
    }

    def "bloqueia remoção de extra já editado"() {
        given:
        def selecao = selecao(StatusSelecao.EM_EDICAO)
        def item = itemExtra(selecao.id, true)
        selecaoRepository.findFirstByAgendamentoIdAndStatusInOrderByNumeroDesc(agendamentoId, _) >>
            Optional.of(selecao)
        selecaoRepository.findByIdForUpdate(selecao.id) >> Optional.of(selecao)
        itemRepository.findBySelecaoIdAndFotoId(selecao.id, item.fotoId) >> Optional.of(item)

        when:
        service.removerExtra(agendamentoId, item.fotoId)

        then:
        thrown(SelecaoNaoEditavelException)
        0 * itemRepository.delete(_)
        0 * eventPublisher.publishEvent(_)
    }

    def "não remove item do pacote ao limpar o carrinho"() {
        given:
        def selecao = selecao(StatusSelecao.EM_EDICAO)
        def item = SelecaoEnsaioItem.builder()
            .id(UUID.randomUUID())
            .selecaoId(selecao.id)
            .fotoId(UUID.randomUUID())
            .numeroGaleria(1)
            .tipo(TipoItemSelecao.PACOTE)
            .ordem(0)
            .editada(false)
            .build()
        selecaoRepository.findFirstByAgendamentoIdAndStatusInOrderByNumeroDesc(agendamentoId, _) >>
            Optional.of(selecao)
        selecaoRepository.findByIdForUpdate(selecao.id) >> Optional.of(selecao)
        itemRepository.findBySelecaoIdAndFotoId(selecao.id, item.fotoId) >> Optional.of(item)

        when:
        service.removerExtra(agendamentoId, item.fotoId)

        then:
        0 * itemRepository.delete(_)
        0 * eventPublisher.publishEvent(_)
    }

    private SelecaoEnsaioItem itemExtra(UUID selecaoId, boolean editada) {
        return SelecaoEnsaioItem.builder()
            .id(UUID.randomUUID())
            .selecaoId(selecaoId)
            .fotoId(UUID.randomUUID())
            .numeroGaleria(3)
            .tipo(TipoItemSelecao.EXTRA)
            .ordem(2)
            .nomeArquivo("IMG_3.jpg")
            .valorUnitario(new BigDecimal("20.00"))
            .editada(editada)
            .build()
    }

    private Agendamento agendamento(int quantidade) {
        return Agendamento.builder()
            .id(agendamentoId)
            .pacote(Pacote.builder().quantidadeFotos(quantidade).nome("Ensaio").build())
            .build()
    }

    private GaleriaEnsaio galeria() {
        return galeria(ModoGaleria.COM_EDICAO)
    }

    private GaleriaEnsaio galeria(ModoGaleria modo) {
        return GaleriaEnsaio.builder()
            .agendamentoId(agendamentoId)
            .modo(modo)
            .fase(modo.requerEdicao() ? FaseGaleria.SELECAO : FaseGaleria.SELECAO_ENVIADA)
            .build()
    }

    private FotoEnsaio foto(int numero) {
        return FotoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .numeroGaleria(numero)
            .ordem(numero - 1)
            .fileName("IMG_${numero}.jpg")
            .status(StatusFoto.PUBLICADA)
            .visivel(true)
            .build()
    }

    private SelecaoEnsaio selecao(StatusSelecao status) {
        return SelecaoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .numero(1)
            .status(status)
            .enviadaEm(LocalDateTime.now())
            .build()
    }

    private SelecaoEnsaioItem item(int numero, boolean editada) {
        return SelecaoEnsaioItem.builder()
            .id(UUID.randomUUID())
            .selecaoId(UUID.randomUUID())
            .fotoId(UUID.randomUUID())
            .numeroGaleria(numero)
            .tipo(TipoItemSelecao.PACOTE)
            .ordem(numero - 1)
            .nomeArquivo("IMG_${numero}.jpg")
            .editada(editada)
            .build()
    }
}
