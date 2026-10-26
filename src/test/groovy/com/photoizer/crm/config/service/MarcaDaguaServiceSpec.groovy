package com.photoizer.crm.config.service

import spock.lang.Specification
import spock.lang.Subject

class MarcaDaguaServiceSpec extends Specification {

    def configuracaoService = Mock(ConfiguracaoService)

    @Subject
    def service = new MarcaDaguaService(configuracaoService)

    def "usa o nome da contratada"() {
        given:
        configuracaoService.getConfig() >> [
            nomeContratada: 'Carol Oliva Fotografia',
            nomeFotografo: 'Carol'
        ]

        expect:
        service.texto() == '© Carol Oliva Fotografia'
    }

    def "usa o nome do fotografo quando a contratada esta vazia"() {
        given:
        configuracaoService.getConfig() >> [
            nomeContratada: '   ',
            nomeFotografo: 'Carol Oliva'
        ]

        expect:
        service.texto() == '© Carol Oliva'
    }

    def "usa o fallback do estudio quando nao ha nome configurado"() {
        given:
        configuracaoService.getConfig() >> [:]

        expect:
        service.texto() == '© Photoizer Studio'
    }
}
