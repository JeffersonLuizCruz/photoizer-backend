package com.photoizer.crm.financeiro.api

import com.photoizer.crm.agenda.model.OrigemAgendamento
import com.photoizer.crm.agenda.model.StatusAgendamento
import com.photoizer.crm.agenda.repository.AgendamentoRepository
import com.photoizer.crm.auth.repository.UserRepository
import com.photoizer.crm.pacote.repository.PacoteRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.transaction.annotation.Transactional
import spock.lang.Specification

import static com.photoizer.crm.agenda.support.AgendaFixtures.agendamento
import static com.photoizer.crm.agenda.support.AgendaFixtures.novoFotografo
import static com.photoizer.crm.agenda.support.AgendaFixtures.pacote
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["spring.datasource.url=jdbc:h2:mem:financeiro-recebimento;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"])
class FinanceiroRecebimentoIntegrationSpec extends Specification {

    @Autowired
    MockMvc mockMvc

    @Autowired
    AgendamentoRepository agendamentoRepository

    @Autowired
    PacoteRepository pacoteRepository

    @Autowired
    UserRepository userRepository

    def "registra recebimento do avulso com forma e tipo e atualiza o saldo"() {
        given: "um avulso confirmado sem entrada recebida"
        def pacote = pacoteRepository.save(pacote(id: null))
        def fotografo = userRepository.save(novoFotografo())
        def avulso = agendamentoRepository.save(agendamento(
            id: null,
            pacote: pacote,
            fotografo: fotografo,
            origem: OrigemAgendamento.AVULSO,
            status: StatusAgendamento.CONFIRMADO,
            valorTotal: new BigDecimal("1000.00"),
            valorEntradaExigido: new BigDecimal("300.00"),
            valorEntradaPago: BigDecimal.ZERO,
            valorRestante: new BigDecimal("1000.00"),
            valorTotalFinal: new BigDecimal("1000.00"),
            taxaDeslocamento: BigDecimal.ZERO,
            percentualEntrada: new BigDecimal("30.00")))

        when: "o staff registra a entrada"
        def resultado = mockMvc.perform(multipart("/api/v1/financeiro/agendamentos/${avulso.id}/recebimentos")
            .param("valor", "300.00")
            .param("formaPagamento", "PIX")
            .param("tipoPagamento", "ENTRADA")
            .with(user("admin").roles("ADMIN")))

        then: "o pagamento é criado com tipo e forma"
        resultado.andExpect(status().isCreated())
            .andExpect(jsonPath('$.valor').value(300.0d))
            .andExpect(jsonPath('$.formaPagamento').value("PIX"))
            .andExpect(jsonPath('$.tipoPagamento').value("ENTRADA"))

        and: "o saldo do agendamento é atualizado"
        def atualizado = agendamentoRepository.findById(avulso.id).get()
        atualizado.valorEntradaPago == new BigDecimal("300.00")
        atualizado.valorRestante == new BigDecimal("700.00")
    }
}
