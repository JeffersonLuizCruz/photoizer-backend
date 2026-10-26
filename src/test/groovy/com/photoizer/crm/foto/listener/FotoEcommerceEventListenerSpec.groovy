package com.photoizer.crm.foto.listener

import com.photoizer.crm.ecommerce.event.EdicaoPublicadaEvent
import com.photoizer.crm.ecommerce.event.SelecaoEnviadaEvent
import com.photoizer.crm.foto.model.FotoEnsaio
import com.photoizer.crm.foto.model.StatusFoto
import com.photoizer.crm.foto.model.VersaoFoto
import com.photoizer.crm.foto.repository.FotoEnsaioRepository
import spock.lang.Specification
import spock.lang.Subject

class FotoEcommerceEventListenerSpec extends Specification {

    def agendamentoId = UUID.randomUUID()
    def fotoRepository = Mock(FotoEnsaioRepository)

    @Subject
    def listener = new FotoEcommerceEventListener(fotoRepository)

    def "publicação da edição troca a versão e libera a foto da seleção ativa"() {
        given:
        def foto = foto(agendamentoId)
        fotoRepository.findAllById(_) >> [foto]
        fotoRepository.saveAll(_) >> { args -> args[0] as List }

        when:
        listener.handleEdicaoPublicada(new EdicaoPublicadaEvent(
            agendamentoId, UUID.randomUUID(), [foto.id]))

        then:
        foto.versaoAtual == VersaoFoto.EDITADA
        !foto.emSelecaoEdicao
    }

    def "envio da seleção marca as fotos como em seleção"() {
        given:
        def foto = foto(agendamentoId)
        fotoRepository.findAllById(_) >> [foto]
        fotoRepository.saveAll(_) >> { args -> args[0] as List }

        when:
        listener.handleSelecaoEnviada(new SelecaoEnviadaEvent(
            agendamentoId, UUID.randomUUID(), [foto.id], 1, 0, "Cliente", null, null))

        then:
        foto.emSelecaoEdicao
    }

    def "não altera foto de outro agendamento"() {
        given:
        def foto = foto(UUID.randomUUID())
        fotoRepository.findAllById(_) >> [foto]
        fotoRepository.saveAll(_) >> { args -> args[0] as List }

        when:
        listener.handleEdicaoPublicada(new EdicaoPublicadaEvent(
            agendamentoId, UUID.randomUUID(), [foto.id]))

        then:
        foto.versaoAtual == VersaoFoto.ORIGINAL
        foto.emSelecaoEdicao
    }

    private FotoEnsaio foto(UUID agendamentoId) {
        return FotoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .fileName("IMG_1.jpg")
            .originalPath("orig/1.jpg")
            .watermarkedPath("wm/1.jpg")
            .thumbPath("thumb/1.jpg")
            .status(StatusFoto.PUBLICADA)
            .visivel(true)
            .emSelecaoEdicao(true)
            .editadaPath("edited/1.jpg")
            .build()
    }
}
