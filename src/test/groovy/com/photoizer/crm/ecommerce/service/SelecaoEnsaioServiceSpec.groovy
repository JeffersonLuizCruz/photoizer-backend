package com.photoizer.crm.ecommerce.service

import com.photoizer.crm.agenda.model.Agendamento
import com.photoizer.crm.ecommerce.event.EdicaoPublicadaEvent
import com.photoizer.crm.ecommerce.event.SelecaoEnviadaEvent
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
        selecaoRepository.findById(selecao.id) >> Optional.of(selecao)
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
        selecaoRepository.findById(selecao.id) >> Optional.of(selecao)
        itemRepository.findBySelecaoIdOrderByNumeroGaleriaAsc(selecao.id) >> itens
        galeriaService.obterParaAtualizacao(agendamentoId) >> galeria()
        selecaoRepository.save(_ as SelecaoEnsaio) >> { args -> args[0] }
        fotoRepository.findAllById(_ as Iterable) >> fotos

        when:
        def resposta = service.publicar(agendamentoId, selecao.id)

        then:
        resposta.status == 'PUBLICADA'
        1 * eventPublisher.publishEvent({ it instanceof EdicaoPublicadaEvent
            && it.agendamentoId() == agendamentoId
            && it.fotoIds().size() == 2 })
    }

    def "bloqueia reabertura de seleção já publicada"() {
        given:
        def selecao = selecao(StatusSelecao.PUBLICADA)
        selecaoRepository.findById(selecao.id) >> Optional.of(selecao)

        when:
        service.reabrir(agendamentoId, selecao.id)

        then:
        thrown(SelecaoNaoEditavelException)
        0 * selecaoRepository.save(_)
    }

    private Agendamento agendamento(int quantidade) {
        return Agendamento.builder()
            .id(agendamentoId)
            .pacote(Pacote.builder().quantidadeFotos(quantidade).nome("Ensaio").build())
            .build()
    }

    private GaleriaEnsaio galeria() {
        return GaleriaEnsaio.builder()
            .agendamentoId(agendamentoId)
            .modo(ModoGaleria.COM_EDICAO)
            .fase(FaseGaleria.SELECAO)
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
