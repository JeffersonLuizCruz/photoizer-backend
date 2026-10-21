package com.photoizer.crm.agenda.api

import com.photoizer.crm.auth.repository.UserRepository
import com.photoizer.crm.pacote.repository.PacoteRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.transaction.annotation.Transactional
import spock.lang.Specification

import java.time.LocalDateTime

import static com.photoizer.crm.agenda.support.AgendaFixtures.novoFotografo
import static com.photoizer.crm.agenda.support.AgendaFixtures.pacote
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@org.springframework.test.context.TestPropertySource(properties = ["spring.datasource.url=jdbc:h2:mem:agenda-controller;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"])
class AgendamentoControllerIntegrationSpec extends Specification {

    @Autowired
    MockMvc mockMvc

    @Autowired
    PacoteRepository pacoteRepository

    @Autowired
    UserRepository userRepository

    def "cria proposta via endpoint e retorna o link público"() {
        given: "pacote e fotógrafo cadastrados"
        def pacote = pacoteRepository.save(pacote(id: null))
        def fotografo = userRepository.save(novoFotografo())
        def corpo = """
            {
              "pacoteId": "${pacote.id}",
              "fotografoId": "${fotografo.id}",
              "dataHoraEnsaio": "${LocalDateTime.now().plusDays(15)}",
              "duracaoMinutos": 60,
              "localEnsaio": "Estúdio"
            }
        """

        when: "o endpoint de criação de proposta é chamado"
        def resultado = mockMvc.perform(post("/api/v1/agendamentos/proposta")
            .contentType(MediaType.APPLICATION_JSON)
            .with(user("staff").roles("ADMIN"))
            .content(corpo))

        then: "a proposta é criada em pré-reserva com token público"
        resultado.andExpect(status().isCreated())
            .andExpect(jsonPath('$.status').value("PRE_RESERVA"))
            .andExpect(jsonPath('$.tokenProposta').isNotEmpty())
    }

    def "cria proposta com entrada já paga via endpoint"() {
        given: "pacote e fotógrafo cadastrados"
        def pacote = pacoteRepository.save(pacote(id: null))
        def fotografo = userRepository.save(novoFotografo())
        def corpo = """
            {
              "pacoteId": "${pacote.id}",
              "fotografoId": "${fotografo.id}",
              "dataHoraEnsaio": "${LocalDateTime.now().plusDays(15)}",
              "duracaoMinutos": 60,
              "localEnsaio": "Estúdio",
              "valorEntradaPago": 200.00
            }
        """

        when: "o endpoint de criação recebe a entrada já paga"
        def resultado = mockMvc.perform(post("/api/v1/agendamentos/proposta")
            .contentType(MediaType.APPLICATION_JSON)
            .with(user("staff").roles("ADMIN"))
            .content(corpo))

        then: "a entrada informada substitui o percentual configurado"
        resultado.andExpect(status().isCreated())
            .andExpect(jsonPath('$.valorEntradaExigido').value(200.0d))
            .andExpect(jsonPath('$.valorEntradaPago').value(200.0d))
            .andExpect(jsonPath('$.valorRestante').value(800.0d))
            .andExpect(jsonPath('$.percentualEntrada').value(20.0d))
    }

    def "cria agendamento avulso via endpoint com papel AGENDADOR"() {
        given: "pacote e fotógrafo cadastrados"
        def pacote = pacoteRepository.save(pacote(id: null))
        def fotografo = userRepository.save(novoFotografo())
        def corpo = """
            {
              "pacoteId": "${pacote.id}",
              "fotografoId": "${fotografo.id}",
              "dataHoraEnsaio": "${LocalDateTime.now().plusDays(15)}",
              "duracaoMinutos": 60,
              "localEnsaio": "Praia",
              "valorJaRecebido": 200.00
            }
        """

        when: "o endpoint de agendamento avulso é chamado"
        def resultado = mockMvc.perform(post("/api/v1/agendamentos/avulso")
            .contentType(MediaType.APPLICATION_JSON)
            .with(user("staff").roles("AGENDADOR"))
            .content(corpo))

        then: "o agendamento nasce confirmado, com reserva recebida e token público"
        resultado.andExpect(status().isCreated())
            .andExpect(jsonPath('$.status').value("CONFIRMADO"))
            .andExpect(jsonPath('$.origem').value("AVULSO"))
            .andExpect(jsonPath('$.valorEntradaPago').value(200.0d))
            .andExpect(jsonPath('$.tokenProposta').isNotEmpty())
    }

    def "nega criação de agendamento avulso para papel FOTOGRAFO"() {
        given: "pacote e fotógrafo cadastrados"
        def pacote = pacoteRepository.save(pacote(id: null))
        def fotografo = userRepository.save(novoFotografo())
        def corpo = """
            {
              "pacoteId": "${pacote.id}",
              "fotografoId": "${fotografo.id}",
              "dataHoraEnsaio": "${LocalDateTime.now().plusDays(15)}",
              "localEnsaio": "Praia"
            }
        """

        when: "o endpoint é chamado"
        def resultado = mockMvc.perform(post("/api/v1/agendamentos/avulso")
            .contentType(MediaType.APPLICATION_JSON)
            .with(user("staff").roles("FOTOGRAFO"))
            .content(corpo))

        then: "o acesso é negado"
        resultado.andExpect(status().isForbidden())
    }

    def "retorna 404 para token de proposta pública inexistente"() {
        when: "um token inválido é consultado"
        def resultado = mockMvc.perform(get("/api/v1/propostas/publico/token-inexistente"))

        then: "a proposta não é encontrada"
        resultado.andExpect(status().isNotFound())
    }
}
