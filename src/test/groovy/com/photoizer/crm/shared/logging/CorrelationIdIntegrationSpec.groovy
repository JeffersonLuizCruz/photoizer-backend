package com.photoizer.crm.shared.logging

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:logging-correlation;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
])
class CorrelationIdIntegrationSpec extends Specification {

    @Autowired
    MockMvc mockMvc

    def "reutiliza o X-Correlation-ID no header e no corpo de erro"() {
        when:
        def resultado = mockMvc.perform(get("/api/v1/rota-inexistente")
            .header(CorrelationIdFilter.HEADER, "chamado-123")
            .with(user("staff").roles("ADMIN")))

        then:
        resultado.andExpect(status().isNotFound())
            .andExpect(header().string(CorrelationIdFilter.HEADER, "chamado-123"))
            .andExpect(jsonPath('$.correlationId').value("chamado-123"))
            .andExpect(jsonPath('$.status').value(404))
    }

    def "gera um correlation id quando o cliente não envia"() {
        when:
        def resultado = mockMvc.perform(get("/api/v1/rota-inexistente")
            .with(user("staff").roles("ADMIN")))

        then:
        def correlationId = resultado.andReturn().response.getHeader(CorrelationIdFilter.HEADER)
        correlationId != null
        resultado.andExpect(jsonPath('$.correlationId').value(correlationId))
    }
}
