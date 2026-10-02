package com.photoizer.crm.auth.service;

import com.photoizer.crm.auth.config.JwtTokenProvider;
import com.photoizer.crm.auth.model.RefreshToken;
import com.photoizer.crm.auth.model.TokenBlocklist;
import com.photoizer.crm.auth.repository.RefreshTokenRepository;
import com.photoizer.crm.auth.repository.TokenBlocklistRepository;
import com.photoizer.crm.auth.repository.UserRepository;
import com.photoizer.crm.shared.util.HashUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Ciclo de vida dos refresh tokens (achado H1).
 *
 * <ul>
 *   <li>Persiste apenas o hash SHA-256 do token, nunca o valor em claro.</li>
 *   <li>Rotação: cada {@code /refresh} invalida o token usado e emite um novo
 *       na mesma família.</li>
 *   <li>Reuse detection: reapresentar um token já consumido/revogado revoga a
 *       família inteira.</li>
 *   <li>Expurgo agendado de tokens e entradas de blocklist expirados (M2).</li>
 * </ul>
 */
@Service
@Transactional
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenBlocklistRepository tokenBlocklistRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               TokenBlocklistRepository tokenBlocklistRepository,
                               UserRepository userRepository,
                               JwtTokenProvider jwtTokenProvider) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenBlocklistRepository = tokenBlocklistRepository;
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Cria um novo refresh token (nova família) e devolve o valor bruto do JWT.
     * Apenas o hash é persistido.
     */
    public String createRefreshToken(UUID userId, String email, String papel) {
        var tokenValue = jwtTokenProvider.generateRefreshToken(userId, email, papel);
        var expiresAt = jwtTokenProvider.getExpirationFromToken(tokenValue).toInstant();
        var refreshToken = RefreshToken.createRoot(HashUtils.sha256(tokenValue), userId, expiresAt);
        refreshTokenRepository.save(refreshToken);
        return tokenValue;
    }

    /**
     * Rotaciona o refresh token: valida, invalida o atual e emite um novo par
     * (access + refresh) na mesma família. Detecta reuso e revoga a família.
     *
     * <p>{@code noRollbackFor} garante que a revogação da família persista mesmo
     * com o lançamento de {@link BadCredentialsException} em token reutilizado.
     */
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public RefreshResult refreshAccessToken(String refreshTokenValue) {
        if (!jwtTokenProvider.validateToken(refreshTokenValue)) {
            throw new BadCredentialsException("Refresh token inválido");
        }

        if (!jwtTokenProvider.isRefreshToken(refreshTokenValue)) {
            throw new BadCredentialsException("Token não é um refresh token");
        }

        var tokenHash = HashUtils.sha256(refreshTokenValue);
        var storedToken = refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
            .orElseThrow(() -> new BadCredentialsException("Refresh token inválido"));

        // Reuse detection: token já consumido ou revogado → derruba a família.
        if (storedToken.isUsado() || storedToken.isRevogado()) {
            refreshTokenRepository.deleteByFamilyId(storedToken.getFamilyId());
            log.warn("Reuso de refresh token detectado (userId={}). Família revogada.",
                storedToken.getUserId());
            throw new BadCredentialsException("Refresh token reutilizado; sessão revogada");
        }

        if (storedToken.isExpired()) {
            refreshTokenRepository.delete(storedToken);
            throw new BadCredentialsException("Refresh token expirado");
        }

        var userId = storedToken.getUserId();
        var user = userRepository.findById(userId)
            .orElseThrow(() -> new BadCredentialsException("Usuário não encontrado"));

        if (!user.isAtivo()) {
            throw new BadCredentialsException("Usuário inativo");
        }

        // Rotação: marca o atual como usado e emite um substituto na família.
        storedToken.marcarUsado();
        refreshTokenRepository.save(storedToken);

        var email = user.getEmail();
        var papel = user.getPapel().name();
        var newRefreshValue = jwtTokenProvider.generateRefreshToken(userId, email, papel);
        var newExpiresAt = jwtTokenProvider.getExpirationFromToken(newRefreshValue).toInstant();
        refreshTokenRepository.save(RefreshToken.createReplacement(
            HashUtils.sha256(newRefreshValue), userId, storedToken.getFamilyId(), newExpiresAt));

        var newAccessToken = jwtTokenProvider.generateToken(userId, email, papel);
        return new RefreshResult(newAccessToken, newRefreshValue);
    }

    public void revokeRefreshToken(String refreshTokenValue) {
        refreshTokenRepository.findByTokenHash(HashUtils.sha256(refreshTokenValue))
            .ifPresent(refreshTokenRepository::delete);
    }

    public void revokeAllRefreshTokens(UUID userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    public void blockAccessToken(String tokenValue) {
        if (jwtTokenProvider.validateToken(tokenValue)) {
            var jti = jwtTokenProvider.getJtiFromToken(tokenValue);
            var expiresAt = jwtTokenProvider.getExpirationFromToken(tokenValue).toInstant();
            var blocklist = TokenBlocklist.create(jti, expiresAt);
            tokenBlocklistRepository.save(blocklist);
        }
    }

    @Transactional(readOnly = true)
    public boolean isTokenBlocked(String tokenValue) {
        if (!jwtTokenProvider.validateToken(tokenValue)) {
            return false;
        }
        var jti = jwtTokenProvider.getJtiFromToken(tokenValue);
        return tokenBlocklistRepository.existsByJti(jti);
    }

    /**
     * Expurgo diário de refresh tokens e entradas de blocklist expirados (M2),
     * evitando crescimento indefinido das tabelas.
     */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void expurgarExpirados() {
        var agora = Instant.now();
        var refreshRemovidos = refreshTokenRepository.deleteByExpiresAtBefore(agora);
        var blocklistRemovidos = tokenBlocklistRepository.deleteByExpiresAtBefore(agora);
        if (refreshRemovidos > 0 || blocklistRemovidos > 0) {
            log.info("Expurgo de tokens: {} refresh e {} entradas de blocklist removidos",
                refreshRemovidos, blocklistRemovidos);
        }
    }

    public record RefreshResult(String accessToken, String refreshToken) {
    }
}
