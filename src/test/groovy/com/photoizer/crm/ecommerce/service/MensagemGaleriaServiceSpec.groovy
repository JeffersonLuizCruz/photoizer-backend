package com.photoizer.crm.ecommerce.service

import spock.lang.Specification

class MensagemGaleriaServiceSpec extends Specification {

    def "resolve as redes sociais e preserva o placeholder do link"() {
        given:
        def service = new MensagemGaleriaService(
            "https://www.instagram.com/rannyamaral_fotografia/",
            "https://www.facebook.com/RannyAmaralfotografia/")

        when:
        def mensagem = service.mensagem()

        then:
        mensagem.contains("Instagram:\nhttps://www.instagram.com/rannyamaral_fotografia/")
        mensagem.contains("Facebook:\nhttps://www.facebook.com/RannyAmaralfotografia/")
        mensagem.contains(MensagemGaleriaService.PLACEHOLDER_LINK)
        mensagem.contains("disponíveis para seleção")
        mensagem.contains("Selecione as fotos do seu pacote")
        mensagem.contains("📸 Ranny Amaral ❤️")
        !mensagem.contains("download em alta resolução")
        !mensagem.contains("Quando postar")
        !mensagem.endsWith("\n")
    }

    def "remove espaços em branco das URLs configuradas"() {
        given:
        def service = new MensagemGaleriaService(
            "  https://www.instagram.com/estudio/  ",
            "  https://www.facebook.com/estudio/  ")

        when:
        def mensagem = service.mensagem()

        then:
        mensagem.contains("https://www.instagram.com/estudio/")
        mensagem.contains("https://www.facebook.com/estudio/")
        !mensagem.contains("  https://")
    }
}
