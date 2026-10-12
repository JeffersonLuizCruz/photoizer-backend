package com.photoizer.crm.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste de contrato do OpenAPI.
 *
 * <p>Garante que a especificação gera sem erro e mantém as invariantes centrais
 * da documentação (título, schema de erro, segurança por endpoint). Roda no
 * build padrão ({@code mvn verify}), servindo de guarda de regressão em CI.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("A especificação OpenAPI deve ser gerada com os metadados e componentes esperados")
    void specDeveConterMetadadosEComponentes() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.info.title").value("Photoizer CRM API"))
            .andExpect(jsonPath("$.components.schemas.ErrorResponse").exists())
            .andExpect(jsonPath("$.components.securitySchemes.bearerAuth").exists())
            .andExpect(jsonPath("$.components.securitySchemes.cookieAuth").exists())
            .andExpect(jsonPath("$.paths").isNotEmpty());
    }

    @Test
    @DisplayName("Endpoint protegido deve declarar segurança; endpoint público não")
    void segurancaDeveRefletirAutorizacao() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            // Clientes exige @RolesAllowed(ADMIN/AGENDADOR) -> security derivado pelo customizer
            .andExpect(jsonPath("$.paths['/api/v1/clientes'].get.security").exists())
            // Login é público -> sem requisito de segurança
            .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security").doesNotExist());
    }
}
