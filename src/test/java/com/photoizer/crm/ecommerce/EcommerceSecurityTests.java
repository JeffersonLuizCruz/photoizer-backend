package com.photoizer.crm.ecommerce;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EcommerceSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void original_exigeAutenticacao() throws Exception {
        mockMvc.perform(get("/api/v1/agendamentos/{id}/fotos/{fotoId}/original",
                UUID.randomUUID(), UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void original_naoDisponivelParaCliente() throws Exception {
        mockMvc.perform(get("/api/v1/agendamentos/{id}/fotos/{fotoId}/original",
                UUID.randomUUID(), UUID.randomUUID())
                .with(user("cliente").roles("CLIENTE")))
            .andExpect(status().isForbidden());
    }

    @Test
    void original_liberadoParaEquipe() throws Exception {
        mockMvc.perform(get("/api/v1/agendamentos/{id}/fotos/{fotoId}/original",
                UUID.randomUUID(), UUID.randomUUID())
                .with(user("fotografo").roles("FOTOGRAFO")))
            .andExpect(result -> {
                int code = result.getResponse().getStatus();
                if (code == 401 || code == 403) {
                    throw new AssertionError("Equipe não deveria ser bloqueada no endpoint do original: HTTP " + code);
                }
            });
    }

    @Test
    void comprovanteAdmin_exigeAutenticacao() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ecommerce/compras/{id}/comprovante", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void comprovanteAdmin_naoDisponivelParaCliente() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ecommerce/compras/{id}/comprovante", UUID.randomUUID())
                .with(user("cliente").roles("CLIENTE")))
            .andExpect(status().isForbidden());
    }

    @Test
    void comprovanteAdmin_liberadoParaAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ecommerce/compras/{id}/comprovante", UUID.randomUUID())
                .with(user("admin").roles("ADMIN")))
            .andExpect(result -> {
                int code = result.getResponse().getStatus();
                if (code == 401 || code == 403) {
                    throw new AssertionError("Admin não deveria ser bloqueado no endpoint do comprovante: HTTP " + code);
                }
            });
    }

    @Test
    void sessaoInvalida_rejeitadaCom422() throws Exception {
        var uuid = UUID.randomUUID();
        var sessaoForjada = uuid + ".aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        mockMvc.perform(get("/api/v1/ecommerce/galeria/{token}/carrinho", uuid)
                .header("X-Session-Id", sessaoForjada))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("Sessão inválida"));
    }

    @Test
    void sessaoAusente_rejeitadaCom422() throws Exception {
        mockMvc.perform(get("/api/v1/ecommerce/galeria/{token}/carrinho", UUID.randomUUID()))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("Sessão inválida"));
    }

    @Test
    void confirmarCompra_exigeAutenticacao() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/ecommerce/compras/{id}/confirmar", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void confirmarCompra_naoDisponivelParaCliente() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/ecommerce/compras/{id}/confirmar", UUID.randomUUID())
                .with(user("cliente").roles("CLIENTE")))
            .andExpect(status().isForbidden());
    }

    @Test
    void confirmarCompra_liberadoParaAdmin() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/ecommerce/compras/{id}/confirmar", UUID.randomUUID())
                .with(user("admin").roles("ADMIN")))
            .andExpect(result -> {
                int code = result.getResponse().getStatus();
                if (code == 401 || code == 403) {
                    throw new AssertionError("Admin não deveria ser bloqueado no endpoint de confirmação: HTTP " + code);
                }
            });
    }
}
