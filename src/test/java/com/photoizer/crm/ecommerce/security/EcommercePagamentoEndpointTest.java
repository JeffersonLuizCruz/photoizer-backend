package com.photoizer.crm.ecommerce.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EcommercePagamentoEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("PIX do e-commerce deve ser público para o checkout da galeria")
    void pixPublicoNaoExigeAutenticacao() throws Exception {
        mockMvc.perform(get("/api/v1/ecommerce/pagamento"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pixChave").value(nullValue()))
            .andExpect(jsonPath("$.pixTipoChave").value(nullValue()));
    }

    @Test
    @DisplayName("Configuração administrativa de pagamento deve exigir autenticação")
    void configuracaoAdminExigeAutenticacao() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ecommerce/pagamento"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Fotógrafo não pode consultar/alterar a configuração de PIX")
    void fotografoNaoAcessaConfiguracaoDePix() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ecommerce/pagamento")
                .with(user("fotografo").roles("FOTOGRAFO")))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN pode consultar a configuração de PIX")
    void adminAcessaConfiguracaoDePix() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ecommerce/pagamento")
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk());
    }
}
