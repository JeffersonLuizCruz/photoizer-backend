package com.photoizer.crm.ecommerce.service

import com.photoizer.crm.agenda.model.Agendamento
import com.photoizer.crm.ecommerce.exception.FotoJaSelecionadaException
import com.photoizer.crm.ecommerce.exception.LimitePacoteExcedidoException
import com.photoizer.crm.ecommerce.model.GaleriaEnsaio
import com.photoizer.crm.foto.model.FotoEnsaio
import com.photoizer.crm.foto.model.StatusFoto
import com.photoizer.crm.foto.repository.FotoEnsaioRepository
import com.photoizer.crm.pacote.model.Pacote
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification
import spock.lang.Subject

import java.time.LocalDateTime

class SelecaoFotosServiceSpec extends Specification {

    def agendamentoId = UUID.randomUUID()
    def fotoId = UUID.randomUUID()
    def fotoRepository = Mock(FotoEnsaioRepository)
    def galeriaQueryService = Mock(GaleriaQueryService)
    def galeriaEnsaioService = Mock(GaleriaEnsaioService)
    def selecaoFotoValidator = Mock(SelecaoFotoValidator)
    def eventPublisher = Mock(ApplicationEventPublisher)

    @Subject
    def service = new SelecaoFotosService(
        fotoRepository,
        galeriaQueryService,
        galeriaEnsaioService,
        selecaoFotoValidator,
        eventPublisher,
    )

    def "override nao permite incluir foto acima do limite do pacote"() {
        given:
        def agendamento = agendamento(quantidadeFotos: 3)
        def foto = foto(selecionada: false)
        galeriaQueryService.buscarAgendamentoPorId(agendamentoId) >> agendamento
        galeriaEnsaioService.obter(agendamentoId) >> GaleriaEnsaio.builder().build()
        fotoRepository.findById(fotoId) >> Optional.of(foto)
        fotoRepository.countSelecionadasPacoteByAgendamentoId(agendamentoId) >> 3

        when:
        service.overrideSelecao(agendamentoId, fotoId, true)

        then:
        thrown(LimitePacoteExcedidoException)
        0 * eventPublisher.publishEvent(_)
    }

    def "override nao permite remover foto ja baixada do pacote"() {
        given:
        def agendamento = agendamento(quantidadeFotos: 10)
        def foto = foto(selecionada: true, download: LocalDateTime.now())
        galeriaQueryService.buscarAgendamentoPorId(agendamentoId) >> agendamento
        galeriaEnsaioService.obter(agendamentoId) >> GaleriaEnsaio.builder().build()
        fotoRepository.findById(fotoId) >> Optional.of(foto)

        when:
        service.overrideSelecao(agendamentoId, fotoId, false)

        then:
        thrown(FotoJaSelecionadaException)
        0 * eventPublisher.publishEvent(_)
    }

    private Agendamento agendamento(Map overrides = [:]) {
        return Agendamento.builder()
            .id(agendamentoId)
            .pacote(Pacote.builder().quantidadeFotos(overrides.quantidadeFotos ?: 10).build())
            .build()
    }

    private FotoEnsaio foto(Map overrides = [:]) {
        return FotoEnsaio.builder()
            .id(fotoId)
            .agendamentoId(agendamentoId)
            .fileName("IMG_1.jpg")
            .status(StatusFoto.PUBLICADA)
            .selecionadaPacote(overrides.selecionada ?: false)
            .dataDownload(overrides.download)
            .build()
    }
}
