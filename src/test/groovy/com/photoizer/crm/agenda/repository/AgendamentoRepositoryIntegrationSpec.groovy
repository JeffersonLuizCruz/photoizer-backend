package com.photoizer.crm.agenda.repository

import com.photoizer.crm.agenda.model.Agendamento
import com.photoizer.crm.agenda.model.StatusAgendamento
import com.photoizer.crm.agenda.service.DisponibilidadeService
import com.photoizer.crm.auth.repository.UserRepository
import com.photoizer.crm.cliente.repository.ClienteRepository
import com.photoizer.crm.pacote.repository.PacoteRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import spock.lang.Specification
import spock.lang.Unroll

import java.time.LocalDate
import java.time.LocalDateTime

import static com.photoizer.crm.agenda.support.AgendaFixtures.agendamento
import static com.photoizer.crm.agenda.support.AgendaFixtures.cliente
import static com.photoizer.crm.agenda.support.AgendaFixtures.novoFotografo
import static com.photoizer.crm.agenda.support.AgendaFixtures.pacote

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@org.springframework.test.context.TestPropertySource(properties = ["spring.datasource.url=jdbc:h2:mem:agendarepo;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"])
class AgendamentoRepositoryIntegrationSpec extends Specification {

    static final LocalDate DIA = LocalDate.of(2027, 5, 20)

    @Autowired
    AgendamentoRepository agendamentoRepository

    @Autowired
    PacoteRepository pacoteRepository

    @Autowired
    ClienteRepository clienteRepository

    @Autowired
    UserRepository userRepository

    @Unroll
    def "consulta ativa por fotógrafo respeita o status #statusAtivo (visível=#visivel)"() {
        given: "dois agendamentos do mesmo fotógrafo no dia"
        def fotografo = userRepository.save(novoFotografo())
        def pacote = pacoteRepository.save(pacote(id: null))
        def visivelEntity = salvar(pacote, fotografo, StatusAgendamento.valueOf(statusAtivo), 8)
        salvar(pacote, fotografo, StatusAgendamento.FINALIZADO, 10)

        when: "a agenda do fotógrafo é consultada"
        def resultado = agendamentoRepository.findActiveByFotografoAndDataBetween(
            fotografo.id, DIA.atStartOfDay(), DIA.atTime(23, 59, 59),
            DisponibilidadeService.STATUS_IGNORADOS)

        then: "somente o status ativo é retornado"
        (resultado*.id == [visivelEntity.id]) == visivel

        where:
        statusAtivo                 | visivel
        "CONFIRMADO"                | true
        "REALIZADO"                 | true
        "AGUARDANDO_PAGAMENTO_FINAL"| true
        "EM_EDICAO"                 | true
        "FINALIZADO"                | false
        "CANCELADO"                 | false
        "NO_SHOW"                   | false
        "PRE_RESERVA"               | false
    }

    def "consulta por local retorna apenas agendamentos ativos do mesmo local"() {
        given: "agendamentos em locais diferentes"
        def fotografo = userRepository.save(novoFotografo())
        def pacote = pacoteRepository.save(pacote(id: null))
        def estudio = salvar(pacote, fotografo, StatusAgendamento.CONFIRMADO, 8, "Estúdio")
        salvar(pacote, fotografo, StatusAgendamento.CONFIRMADO, 10, "Praia")
        salvar(pacote, fotografo, StatusAgendamento.FINALIZADO, 12, "Estúdio")

        when: "o local é consultado"
        def resultado = agendamentoRepository.findActiveByLocalAndDataBetween(
            "Estúdio", DIA.atStartOfDay(), DIA.atTime(23, 59, 59),
            DisponibilidadeService.STATUS_IGNORADOS)

        then: "somente o agendamento ativo do local é retornado"
        resultado*.id == [estudio.id]
    }

    def "consulta de bloqueio de dia inteiro considera apenas pacotes com a flag"() {
        given: "um pacote que bloqueia o dia e outro que não bloqueia"
        def fotografo = userRepository.save(novoFotografo())
        def bloqueia = pacoteRepository.save(pacote(id: null, bloqueiaDiaInteiro: true))
        def naoBloqueia = pacoteRepository.save(pacote(id: null, bloqueiaDiaInteiro: false))
        def comBloqueio = salvar(bloqueia, fotografo, StatusAgendamento.CONFIRMADO, 8)
        salvar(naoBloqueia, fotografo, StatusAgendamento.CONFIRMADO, 10)

        when: "os agendamentos de dia inteiro são consultados"
        def resultado = agendamentoRepository.findBloqueiaDiaInteiroByFotografoAndDataBetween(
            fotografo.id, DIA.atStartOfDay(), DIA.atTime(23, 59, 59),
            DisponibilidadeService.STATUS_IGNORADOS)

        then: "somente o pacote com bloqueio é retornado"
        resultado*.id == [comBloqueio.id]
    }

    def "exclusão por id remove o próprio agendamento da consulta"() {
        given: "dois agendamentos ativos do mesmo fotógrafo"
        def fotografo = userRepository.save(novoFotografo())
        def pacote = pacoteRepository.save(pacote(id: null))
        def primeiro = salvar(pacote, fotografo, StatusAgendamento.CONFIRMADO, 8)
        def segundo = salvar(pacote, fotografo, StatusAgendamento.CONFIRMADO, 10)

        when: "o primeiro é excluído da consulta"
        def resultado = agendamentoRepository.findActiveByFotografoAndDataBetweenExcludingId(
            fotografo.id, DIA.atStartOfDay(), DIA.atTime(23, 59, 59),
            DisponibilidadeService.STATUS_IGNORADOS, primeiro.id)

        then: "somente o segundo é retornado"
        resultado*.id == [segundo.id]
    }

    def "busca por hash do token público retorna o agendamento com pacote e editor"() {
        given: "uma proposta com token público"
        def fotografo = userRepository.save(novoFotografo())
        def pacote = pacoteRepository.save(pacote(id: null))
        def proposta = salvar(pacote, fotografo, StatusAgendamento.PRE_RESERVA, 8)
        proposta.tokenPropostaHash = "hash-publico"
        agendamentoRepository.save(proposta)

        when: "o hash é consultado"
        def resultado = agendamentoRepository.findByTokenPropostaHash("hash-publico")

        then: "a proposta é retornada"
        resultado.present
        resultado.get().id == proposta.id
    }

    def "busca com lock pessimista retorna o agendamento"() {
        given: "um agendamento persistido"
        def fotografo = userRepository.save(novoFotografo())
        def pacote = pacoteRepository.save(pacote(id: null))
        def agendamento = salvar(pacote, fotografo, StatusAgendamento.PRE_RESERVA, 8)

        when: "o agendamento é buscado com lock"
        def resultado = agendamentoRepository.findByIdWithLock(agendamento.id)

        then: "o registro é retornado"
        resultado.present
        resultado.get().id == agendamento.id
    }

    private Agendamento salvar(def pacote, def fotografo, StatusAgendamento status, int hora,
                               String local = "Estúdio") {
        def cliente = clienteRepository.save(cliente(id: null, telefone: UUID.randomUUID().toString().take(20)))
        agendamentoRepository.save(agendamento(
            id: null,
            pacote: pacote,
            cliente: cliente,
            fotografo: fotografo,
            status: status,
            dataHoraEnsaio: LocalDateTime.of(DIA, java.time.LocalTime.of(hora, 0)),
            localEnsaio: local))
    }
}
