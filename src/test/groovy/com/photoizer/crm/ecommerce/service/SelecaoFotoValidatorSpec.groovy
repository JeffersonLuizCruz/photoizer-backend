package com.photoizer.crm.ecommerce.service

import com.photoizer.crm.agenda.model.Agendamento
import com.photoizer.crm.ecommerce.exception.FotoIndisponivelException
import com.photoizer.crm.foto.model.FotoEnsaio
import com.photoizer.crm.foto.model.StatusFoto
import com.photoizer.crm.pacote.model.Pacote
import spock.lang.Specification
import spock.lang.Subject

class SelecaoFotoValidatorSpec extends Specification {

    def agendamentoId = UUID.randomUUID()

    @Subject
    def validator = new SelecaoFotoValidator()

    def "aceita foto publicada, visível, numerada e sem vínculo"() {
        when:
        validator.validar(foto(), agendamento())

        then:
        noExceptionThrown()
    }

    def "rejeita foto ainda não publicada"() {
        when:
        validator.validar(foto(status: StatusFoto.INEDITA), agendamento())

        then:
        thrown(FotoIndisponivelException)
    }

    def "rejeita foto oculta"() {
        when:
        validator.validar(foto(visivel: false), agendamento())

        then:
        thrown(FotoIndisponivelException)
    }

    def "rejeita foto já baixada"() {
        when:
        validator.validar(foto(dataDownload: java.time.LocalDateTime.now()), agendamento())

        then:
        thrown(FotoIndisponivelException)
    }

    def "rejeita foto já vinculada a compra"() {
        when:
        validator.validar(foto(compraExtraId: UUID.randomUUID()), agendamento())

        then:
        thrown(FotoIndisponivelException)
    }

    def "rejeita foto sem numeração de galeria"() {
        when:
        validator.validar(foto(numeroGaleria: null), agendamento())

        then:
        thrown(FotoIndisponivelException)
    }

    def "rejeita foto de outro agendamento"() {
        when:
        validator.validar(foto(agendamentoId: UUID.randomUUID()), agendamento())

        then:
        thrown(FotoIndisponivelException)
    }

    private Agendamento agendamento() {
        return Agendamento.builder()
            .id(agendamentoId)
            .pacote(Pacote.builder().quantidadeFotos(3).build())
            .build()
    }

    private FotoEnsaio foto(Map overrides = [:]) {
        return FotoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(overrides.agendamentoId ?: agendamentoId)
            .fileName("IMG_1.jpg")
            .originalPath("orig/1.jpg")
            .watermarkedPath("wm/1.jpg")
            .thumbPath("thumb/1.jpg")
            .numeroGaleria(overrides.containsKey('numeroGaleria') ? overrides.numeroGaleria : 1)
            .status(overrides.status ?: StatusFoto.PUBLICADA)
            .visivel(overrides.visivel != null ? overrides.visivel : true)
            .dataDownload(overrides.dataDownload)
            .compraExtraId(overrides.compraExtraId)
            .build()
    }
}
