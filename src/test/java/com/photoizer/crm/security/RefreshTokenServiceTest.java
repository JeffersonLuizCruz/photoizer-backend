package com.photoizer.crm.security;

import com.photoizer.crm.auth.config.JwtTokenProvider;
import com.photoizer.crm.auth.model.Papel;
import com.photoizer.crm.auth.model.RefreshToken;
import com.photoizer.crm.auth.model.TokenBlocklist;
import com.photoizer.crm.auth.model.User;
import com.photoizer.crm.auth.repository.RefreshTokenRepository;
import com.photoizer.crm.auth.repository.TokenBlocklistRepository;
import com.photoizer.crm.auth.repository.UserRepository;
import com.photoizer.crm.auth.service.RefreshTokenService;
import com.photoizer.crm.shared.util.HashUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Testes do ciclo de vida do refresh token (achado H1 + expurgo M2):
 * hash persistido, rotação, detecção de reuso e limpeza de expirados.
 */
@DataJpaTest
class RefreshTokenServiceTest {

    private static final String SECRET =
        "test-secret-key-with-at-least-32-bytes-long-000";

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private TokenBlocklistRepository tokenBlocklistRepository;

    private JwtTokenProvider jwtTokenProvider;
    private RefreshTokenService refreshTokenService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(SECRET, 3_600_000L, 604_800_000L);

        userId = UUID.randomUUID();
        var userRepository = mock(UserRepository.class);
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(
            new User("dono@test.com", "hash", "Dono", Papel.ADMIN)));

        refreshTokenService = new RefreshTokenService(
            refreshTokenRepository, tokenBlocklistRepository, userRepository, jwtTokenProvider);
    }

    @Test
    @DisplayName("Persiste apenas o hash SHA-256, nunca o token em claro")
    void persisteApenasHash() {
        var raw = refreshTokenService.createRefreshToken(userId, "dono@test.com", "ADMIN");

        var stored = refreshTokenRepository.findAll().get(0);
        assertThat(stored.getTokenHash()).isNotEqualTo(raw).hasSize(64);
        assertThat(refreshTokenRepository.findByTokenHash(raw)).isEmpty();
        assertThat(refreshTokenRepository.findByTokenHash(HashUtils.sha256(raw))).isPresent();
    }

    @Test
    @DisplayName("Rotação invalida o token usado e emite novos tokens")
    void rotacionaTokens() {
        var raw1 = refreshTokenService.createRefreshToken(userId, "dono@test.com", "ADMIN");

        var resultado = refreshTokenService.refreshAccessToken(raw1);

        assertThat(resultado.refreshToken()).isNotEqualTo(raw1);
        assertThat(jwtTokenProvider.validateToken(resultado.accessToken())).isTrue();
        assertThat(refreshTokenRepository.findByTokenHash(HashUtils.sha256(resultado.refreshToken())))
            .isPresent();

        var antigo = refreshTokenRepository.findByTokenHash(HashUtils.sha256(raw1)).orElseThrow();
        assertThat(antigo.isUsado()).isTrue();
    }

    @Test
    @DisplayName("Reuso de refresh token revoga toda a família")
    void reusoRevogaFamilia() {
        var raw1 = refreshTokenService.createRefreshToken(userId, "dono@test.com", "ADMIN");
        var resultado = refreshTokenService.refreshAccessToken(raw1);

        assertThatThrownBy(() -> refreshTokenService.refreshAccessToken(raw1))
            .isInstanceOf(BadCredentialsException.class);

        assertThat(refreshTokenRepository.findByTokenHash(HashUtils.sha256(raw1))).isEmpty();
        assertThat(refreshTokenRepository.findByTokenHash(HashUtils.sha256(resultado.refreshToken())))
            .isEmpty();
    }

    @Test
    @DisplayName("Expurgo remove refresh tokens e blocklist expirados (M2)")
    void expurgoRemoveExpirados() {
        var expirado = refreshTokenRepository.save(
            RefreshToken.createRoot(HashUtils.sha256("expirado"), userId, Instant.now().minusSeconds(60)));
        tokenBlocklistRepository.save(
            TokenBlocklist.create("jti-expirado", Instant.now().minusSeconds(60)));

        refreshTokenService.expurgarExpirados();

        assertThat(refreshTokenRepository.findById(expirado.getId())).isEmpty();
        assertThat(tokenBlocklistRepository.existsByJti("jti-expirado")).isFalse();
    }
}
