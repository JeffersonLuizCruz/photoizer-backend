package com.photoizer.crm.security;

import com.photoizer.crm.auth.config.JwtTokenProvider;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes do provedor JWT (achado C1): segredo obrigatório/forte e rejeição de
 * tokens assinados com outro segredo (forja).
 */
class JwtTokenProviderTest {

    private static final String SECRET =
        "test-secret-key-with-at-least-32-bytes-long-000";

    private JwtTokenProvider provider(String secret) {
        return new JwtTokenProvider(secret, 3_600_000L, 604_800_000L);
    }

    @Test
    void tokenAssinadoComSegredoCorretoEhAceito() {
        var p = provider(SECRET);
        var token = p.generateToken(UUID.randomUUID(), "u@x.com", "ADMIN");
        assertThat(p.validateToken(token)).isTrue();
        assertThat(p.getPapelFromToken(token)).isEqualTo("ADMIN");
    }

    @Test
    void tokenAssinadoComOutroSegredoEhRejeitado() {
        var emissor = provider(SECRET);
        var outro = provider("outro-secret-key-com-32-bytes-ou-mais-000");
        var token = emissor.generateToken(UUID.randomUUID(), "u@x.com", "ADMIN");
        assertThat(outro.validateToken(token)).isFalse();
    }

    @Test
    void refreshTokenEhMarcadoComoTypeRefresh() {
        var p = provider(SECRET);
        var token = p.generateRefreshToken(UUID.randomUUID(), "u@x.com", "ADMIN");
        assertThat(p.isRefreshToken(token)).isTrue();
    }

    @Test
    void segredoCurtoLanca() {
        assertThatThrownBy(() -> provider("curto"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("32 bytes");
    }

    @Test
    void segredoVazioLanca() {
        assertThatThrownBy(() -> provider("  "))
            .isInstanceOf(IllegalStateException.class);
    }
}
