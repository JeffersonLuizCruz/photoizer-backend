package com.photoizer.crm.agenda.api

import com.photoizer.crm.agenda.repository.AgendamentoRepository
import com.photoizer.crm.auth.model.Papel
import com.photoizer.crm.auth.model.User
import com.photoizer.crm.auth.repository.UserRepository
import com.photoizer.crm.pacote.repository.PacoteRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.test.web.servlet.MockMvc
import org.springframework.transaction.annotation.Transactional
import spock.lang.Specification

import java.time.LocalDateTime

import static com.photoizer.crm.agenda.support.AgendaFixtures.novoFotografo
import static com.photoizer.crm.agenda.support.AgendaFixtures.pacote
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
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

    @Autowired
    AgendamentoRepository agendamentoRepository

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

    def "registra agendamento avulso com data passada via endpoint"() {
        given: "pacote e fotógrafo cadastrados"
        def pacote = pacoteRepository.save(pacote(id: null))
        def fotografo = userRepository.save(novoFotografo())
        def corpo = """
            {
              "pacoteId": "${pacote.id}",
              "fotografoId": "${fotografo.id}",
              "dataHoraEnsaio": "${LocalDateTime.now().minusDays(2)}",
              "duracaoMinutos": 60,
              "localEnsaio": "Praia"
            }
        """

        when: "o endpoint de agendamento avulso recebe uma data passada"
        def resultado = mockMvc.perform(post("/api/v1/agendamentos/avulso")
            .contentType(MediaType.APPLICATION_JSON)
            .with(user("staff").roles("AGENDADOR"))
            .content(corpo))

        then: "o ensaio é registrado como confirmado"
        resultado.andExpect(status().isCreated())
            .andExpect(jsonPath('$.status').value("CONFIRMADO"))
            .andExpect(jsonPath('$.origem').value("AVULSO"))
    }

    def "rejeita proposta com data passada via endpoint"() {
        given: "pacote e fotógrafo cadastrados"
        def pacote = pacoteRepository.save(pacote(id: null))
        def fotografo = userRepository.save(novoFotografo())
        def corpo = """
            {
              "pacoteId": "${pacote.id}",
              "fotografoId": "${fotografo.id}",
              "dataHoraEnsaio": "${LocalDateTime.now().minusDays(2)}",
              "duracaoMinutos": 60,
              "localEnsaio": "Estúdio"
            }
        """

        when: "o endpoint de proposta recebe uma data passada"
        def resultado = mockMvc.perform(post("/api/v1/agendamentos/proposta")
            .contentType(MediaType.APPLICATION_JSON)
            .with(user("staff").roles("ADMIN"))
            .content(corpo))

        then: "a regra da proposta normal continua bloqueando"
        resultado.andExpect(status().isUnprocessableEntity())
    }

    def "corrige agendamento avulso retroativo via endpoint dedicado"() {
        given: "um avulso retroativo já criado"
        def pacote = pacoteRepository.save(pacote(id: null))
        def fotografo = userRepository.save(novoFotografo())
        def criacao = """
            {
              "pacoteId": "${pacote.id}",
              "fotografoId": "${fotografo.id}",
              "dataHoraEnsaio": "${LocalDateTime.now().minusDays(2)}",
              "duracaoMinutos": 60,
              "localEnsaio": "Praia"
            }
        """
        mockMvc.perform(post("/api/v1/agendamentos/avulso")
            .contentType(MediaType.APPLICATION_JSON)
            .with(user("staff").roles("AGENDADOR"))
            .content(criacao))
            .andExpect(status().isCreated())
        def agendamento = agendamentoRepository.findAll().first()
        def atualizacao = """
            {
              "pacoteId": "${pacote.id}",
              "fotografoId": "${fotografo.id}",
              "dataHoraEnsaio": "${LocalDateTime.now().minusDays(2)}",
              "localEnsaio": "Estúdio corrigido",
              "custoDeslocamento": 0,
              "repassarDeslocamento": true,
              "autorizaUsoImagem": false,
              "observacoes": "Correção retroativa",
              "fotografos": []
            }
        """

        when: "o endpoint dedicado corrige o avulso com data passada"
        def resultado = mockMvc.perform(put("/api/v1/agendamentos/avulso/${agendamento.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .with(user("staff").roles("ADMIN"))
            .content(atualizacao))

        then: "a correção é aceita"
        resultado.andExpect(status().isOk())
            .andExpect(jsonPath('$.localEnsaio').value("Estúdio corrigido"))
            .andExpect(jsonPath('$.observacoes').value("Correção retroativa"))
    }

    def "mantém regra de data futura na atualização normal de agendamento"() {
        given: "uma proposta criada com data futura"
        def pacote = pacoteRepository.save(pacote(id: null))
        def fotografo = userRepository.save(novoFotografo())
        def criacao = """
            {
              "pacoteId": "${pacote.id}",
              "fotografoId": "${fotografo.id}",
              "dataHoraEnsaio": "${LocalDateTime.now().plusDays(10)}",
              "duracaoMinutos": 60,
              "localEnsaio": "Estúdio"
            }
        """
        mockMvc.perform(post("/api/v1/agendamentos/proposta")
            .contentType(MediaType.APPLICATION_JSON)
            .with(user("staff").roles("ADMIN"))
            .content(criacao))
            .andExpect(status().isCreated())
        def agendamento = agendamentoRepository.findAll().first()
        def atualizacao = """
            {
              "pacoteId": "${pacote.id}",
              "fotografoId": "${fotografo.id}",
              "dataHoraEnsaio": "${LocalDateTime.now().minusDays(1)}",
              "localEnsaio": "Estúdio",
              "custoDeslocamento": 0,
              "repassarDeslocamento": true,
              "autorizaUsoImagem": false,
              "observacoes": "Tentativa",
              "fotografos": []
            }
        """

        when: "a atualização normal recebe uma data passada"
        def resultado = mockMvc.perform(put("/api/v1/agendamentos/${agendamento.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .with(user("staff").roles("ADMIN"))
            .content(atualizacao))

        then: "o fluxo normal continua rejeitando data passada"
        resultado.andExpect(status().isUnprocessableEntity())
    }

    def "lista fotógrafos e usa o fotógrafo logado como padrão"() {
        given: "dois fotógrafos ativos cadastrados"
        def ana = userRepository.save(novoFotografo("Ana Fotógrafa"))
        userRepository.save(novoFotografo("Bia Fotógrafa"))

        when: "a fotógrafa Ana consulta as opções"
        def resultado = mockMvc.perform(get("/api/v1/agendamentos/fotografos")
            .with(authentication(new UsernamePasswordAuthenticationToken(
                ana.id.toString(), null, [new SimpleGrantedAuthority("ROLE_FOTOGRAFO")]))))

        then: "ela vem como responsável padrão"
        resultado.andExpect(status().isOk())
            .andExpect(jsonPath('$.fotografoPadraoId').value(ana.id.toString()))
            .andExpect(jsonPath('$.fotografos.length()').value(2))
    }

    def "usa o fotógrafo configurado como padrão para staff que não é fotógrafo"() {
        given: "um fotógrafo provisionado pela configuração e um staff agendador"
        def configurado = novoFotografo("Fotógrafo do Estúdio")
        configurado.setEmail(User.EMAIL_FOTOGRAFO_CONFIGURADO)
        configurado = userRepository.save(configurado)
        def agendador = userRepository.save(new User(
            "agendador-${UUID.randomUUID()}@teste.com", "hash", "Agendador", Papel.AGENDADOR))

        when: "o agendador consulta as opções"
        def resultado = mockMvc.perform(get("/api/v1/agendamentos/fotografos")
            .with(authentication(new UsernamePasswordAuthenticationToken(
                agendador.id.toString(), null, [new SimpleGrantedAuthority("ROLE_AGENDADOR")]))))

        then: "o fotógrafo configurado é o padrão"
        resultado.andExpect(status().isOk())
            .andExpect(jsonPath('$.fotografoPadraoId').value(configurado.id.toString()))
    }

    def "retorna 404 para token de proposta pública inexistente"() {
        when: "um token inválido é consultado"
        def resultado = mockMvc.perform(get("/api/v1/propostas/publico/token-inexistente"))

        then: "a proposta não é encontrada"
        resultado.andExpect(status().isNotFound())
    }
}
