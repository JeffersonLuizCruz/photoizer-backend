package com.photoizer.crm.agenda.security;

import com.photoizer.crm.agenda.api.AgendamentoController;
import jakarta.annotation.security.RolesAllowed;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regressão de segurança: os endpoints de alteração e remoção de proposta
 * exigem os papéis ADMIN/FOTOGRAFO e não devem cair em {@code authenticated()}.
 */
class PropostaEndpointsAutorizacaoTest {

    @Test
    @DisplayName("atualizarProposta exige ADMIN/FOTOGRAFO")
    void atualizarPropostaExigePapeis() {
        assertRoles("atualizarProposta");
    }

    @Test
    @DisplayName("removerProposta exige ADMIN/FOTOGRAFO")
    void removerPropostaExigePapeis() {
        assertRoles("removerProposta");
    }

    private void assertRoles(String nomeMetodo) {
        Method metodo = Arrays.stream(AgendamentoController.class.getMethods())
            .filter(m -> m.getName().equals(nomeMetodo))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Método não encontrado: " + nomeMetodo));

        var roles = metodo.getAnnotation(RolesAllowed.class);
        assertThat(roles)
            .as("@RolesAllowed ausente em %s", nomeMetodo)
            .isNotNull();
        assertThat(Arrays.asList(roles.value()))
            .as("Papéis de %s", nomeMetodo)
            .containsExactlyInAnyOrder("ADMIN", "FOTOGRAFO");
    }
}
