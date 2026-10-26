package com.photoizer.crm.ecommerce.service

import com.photoizer.crm.agenda.model.Agendamento
import com.photoizer.crm.ecommerce.event.ItemCarrinhoAdicionadoEvent
import com.photoizer.crm.ecommerce.event.ItemCarrinhoRemovidoEvent
import com.photoizer.crm.ecommerce.model.ItemCarrinho
import com.photoizer.crm.ecommerce.repository.ItemCarrinhoRepository
import com.photoizer.crm.foto.model.FotoEnsaio
import com.photoizer.crm.foto.model.StatusFoto
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification
import spock.lang.Subject

class CarrinhoServiceSpec extends Specification {

    def token = UUID.randomUUID()
    def sessionId = UUID.randomUUID()
    def agendamentoId = UUID.randomUUID()
    def fotoId = UUID.randomUUID()
    def itemCarrinhoRepository = Mock(ItemCarrinhoRepository)
    def galeriaQueryService = Mock(GaleriaQueryService)
    def eventPublisher = Mock(ApplicationEventPublisher)

    @Subject
    def service = new CarrinhoService(
        itemCarrinhoRepository,
        galeriaQueryService,
        eventPublisher)

    def "publica evento ao adicionar um extra novo ao carrinho"() {
        given:
        galeriaQueryService.buscarAgendamentoPorToken(token) >> agendamento()
        galeriaQueryService.buscarFotoPorId(fotoId) >> foto()
        galeriaQueryService.isFotoDisponivelParaCarrinho(_) >> true
        itemCarrinhoRepository.findBySessionIdAndAgendamentoIdOrderByAuditInfoCreatedAtAsc(sessionId, agendamentoId) >> []
        itemCarrinhoRepository.save(_ as ItemCarrinho) >> { args -> args[0] }

        when:
        service.adicionarAoCarrinho(token, sessionId, fotoId)

        then:
        1 * itemCarrinhoRepository.save(_ as ItemCarrinho)
        1 * eventPublisher.publishEvent({ it instanceof ItemCarrinhoAdicionadoEvent
            && it.agendamentoId() == agendamentoId
            && it.fotoId() == fotoId })
    }

    def "não publica evento quando o extra já está no carrinho"() {
        given:
        galeriaQueryService.buscarAgendamentoPorToken(token) >> agendamento()
        galeriaQueryService.buscarFotoPorId(fotoId) >> foto()
        galeriaQueryService.isFotoDisponivelParaCarrinho(_) >> true
        itemCarrinhoRepository.findBySessionIdAndAgendamentoIdOrderByAuditInfoCreatedAtAsc(sessionId, agendamentoId) >>
            [ItemCarrinho.builder().agendamentoId(agendamentoId).fotoId(fotoId).sessionId(sessionId).build()]

        when:
        service.adicionarAoCarrinho(token, sessionId, fotoId)

        then:
        0 * itemCarrinhoRepository.save(_)
        0 * eventPublisher.publishEvent(_)
    }

    def "publica evento ao remover um item do carrinho"() {
        given:
        galeriaQueryService.buscarAgendamentoPorToken(token) >> agendamento()

        when:
        service.removerDoCarrinho(token, sessionId, fotoId)

        then:
        1 * itemCarrinhoRepository.deleteBySessionIdAndAgendamentoIdAndFotoId(sessionId, agendamentoId, fotoId)
        1 * eventPublisher.publishEvent({ it instanceof ItemCarrinhoRemovidoEvent
            && it.agendamentoId() == agendamentoId
            && it.fotoId() == fotoId })
    }

    private Agendamento agendamento() {
        return Agendamento.builder().id(agendamentoId).build()
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
