package com.photoizer.crm.agenda.service

import com.photoizer.crm.auth.model.Papel
import com.photoizer.crm.auth.model.User
import com.photoizer.crm.auth.repository.UserRepository
import spock.lang.Specification
import spock.lang.Subject

import static com.photoizer.crm.agenda.support.AgendaFixtures.fotografo
import static com.photoizer.crm.agenda.support.AgendaFixtures.usuario

class FotografoOpcoesServiceSpec extends Specification {

    def userRepository = Mock(UserRepository)

    @Subject
    def service = new FotografoOpcoesService(userRepository)

    def "usa o próprio usuário logado quando ele é fotógrafo ativo"() {
        given: "o fotógrafo logado está ativo"
        def logado = fotografo("Ana Fotógrafa")
        userRepository.findById(logado.id) >> Optional.of(logado)
        userRepository.findByPapel(Papel.FOTOGRAFO) >> [logado]

        when: "as opções são resolvidas"
        def opcoes = service.opcoes(logado.id)

        then: "o padrão é o próprio usuário logado"
        opcoes.fotografoPadraoId == logado.id
        opcoes.fotografos*.id == [logado.id]
    }

    def "usa o fotógrafo de Configurações quando o usuário logado não é fotógrafo"() {
        given: "o staff logado é agendador e existe um fotógrafo configurado"
        def agendador = usuario("Agendador", Papel.AGENDADOR)
        def configurado = fotografoConfigurado("Fotógrafo do Estúdio")
        userRepository.findById(agendador.id) >> Optional.of(agendador)
        userRepository.findByEmail(User.EMAIL_FOTOGRAFO_CONFIGURADO) >> Optional.of(configurado)
        userRepository.findByPapel(Papel.FOTOGRAFO) >> [configurado]

        when: "as opções são resolvidas"
        def opcoes = service.opcoes(agendador.id)

        then: "o padrão é o fotógrafo provisionado a partir da configuração"
        opcoes.fotografoPadraoId == configurado.id
    }

    def "ignora o usuário logado inativo e usa o fotógrafo configurado"() {
        given: "o fotógrafo logado está inativo"
        def logado = fotografo("Fotógrafo Inativo")
        logado.setAtivo(false)
        def configurado = fotografoConfigurado("Fotógrafo do Estúdio")
        userRepository.findById(logado.id) >> Optional.of(logado)
        userRepository.findByEmail(User.EMAIL_FOTOGRAFO_CONFIGURADO) >> Optional.of(configurado)
        userRepository.findByPapel(Papel.FOTOGRAFO) >> [configurado]

        when: "as opções são resolvidas"
        def opcoes = service.opcoes(logado.id)

        then: "o padrão cai para o fotógrafo configurado"
        opcoes.fotografoPadraoId == configurado.id
    }

    def "não define padrão quando não há fotógrafo logado nem fotógrafo configurado"() {
        given: "não há fotógrafo configurado nem usuário logado"
        userRepository.findByEmail(User.EMAIL_FOTOGRAFO_CONFIGURADO) >> Optional.empty()
        userRepository.findByPapel(Papel.FOTOGRAFO) >> []

        expect: "a seleção permanece manual"
        service.opcoes(null).fotografoPadraoId == null
    }

    def "ignora fotógrafo configurado inativo"() {
        given: "o fotógrafo configurado está inativo"
        def configurado = fotografoConfigurado("Fotógrafo do Estúdio")
        configurado.setAtivo(false)
        userRepository.findById(_) >> Optional.empty()
        userRepository.findByEmail(User.EMAIL_FOTOGRAFO_CONFIGURADO) >> Optional.of(configurado)
        userRepository.findByPapel(Papel.FOTOGRAFO) >> []

        expect: "não há responsável padrão"
        service.opcoes(UUID.randomUUID()).fotografoPadraoId == null
    }

    private User fotografoConfigurado(String nome) {
        def user = fotografo(nome)
        user.setEmail(User.EMAIL_FOTOGRAFO_CONFIGURADO)
        user
    }
}
