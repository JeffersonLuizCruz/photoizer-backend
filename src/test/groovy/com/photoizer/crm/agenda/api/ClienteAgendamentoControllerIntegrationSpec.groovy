package com.photoizer.crm.agenda.api

import com.photoizer.crm.agenda.model.StatusAgendamento
import com.photoizer.crm.agenda.repository.AgendamentoRepository
import com.photoizer.crm.agenda.support.AgendaFixtures
import com.photoizer.crm.cliente.repository.ClienteRepository
import com.photoizer.crm.pacote.repository.PacoteRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.transaction.annotation.Transactional
import spock.lang.Specification

import java.time.LocalDateTime

import static org.hamcrest.Matchers.hasSize
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:cliente-agendamentos;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
])
class ClienteAgendamentoControllerIntegrationSpec extends Specification {

    @Autowired
    MockMvc mockMvc

    @Autowired
    ClienteRepository clienteRepository

    @Autowired
    PacoteRepository pacoteRepository

    @Autowired
    AgendamentoRepository agendamentoRepository

    def "cliente autenticado lista apenas seus ensaios confirmados"() {
        given: "um cliente com um ensaio confirmado e outro cancelado"
        def cliente = clienteRepository.save(AgendaFixtures.cliente(
            id: null, telefone: "(11) 98888-7777", email: "cliente-integracao@teste.com"))
        def pacote = pacoteRepository.save(AgendaFixtures.pacote(id: null))
        def confirmado = agendamentoRepository.save(AgendaFixtures.agendamento(
            id: null,
            cliente: cliente,
            pacote: pacote,
            status: StatusAgendamento.CONFIRMADO,
            dataHoraEnsaio: LocalDateTime.now().plusDays(2)))
        agendamentoRepository.save(AgendaFixtures.agendamento(
            id: null,
            cliente: cliente,
            pacote: pacote,
            status: StatusAgendamento.CANCELADO,
            dataHoraEnsaio: LocalDateTime.now().plusDays(4)))

        when: "o cliente consulta a área Meus Ensaios"
        def resultado = mockMvc.perform(get("/api/v1/auth/cliente/agendamentos")
            .with(authentication(new UsernamePasswordAuthenticationToken(
                cliente.id.toString(), null, [new SimpleGrantedAuthority("ROLE_CLIENTE")]))))

        then: "somente o ensaio confirmado é retornado"
        resultado.andExpect(status().isOk())
            .andExpect(jsonPath('$', hasSize(1)))
            .andExpect(jsonPath('$[0].id').value(confirmado.id.toString()))
            .andExpect(jsonPath('$[0].status').value("CONFIRMADO"))
            .andExpect(jsonPath('$[0].statusDescricao').value("Confirmado"))
    }

    def "staff não acessa a área de ensaios do cliente"() {
        expect:
        mockMvc.perform(get("/api/v1/auth/cliente/agendamentos")
            .with(user("staff").roles("ADMIN")))
            .andExpect(status().isForbidden())
    }

    def "requisição sem autenticação retorna 401"() {
        expect:
        mockMvc.perform(get("/api/v1/auth/cliente/agendamentos"))
            .andExpect(status().isUnauthorized())
    }
}
