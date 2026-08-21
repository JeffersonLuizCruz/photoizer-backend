package com.photoizer.crm.auth.service;

import com.photoizer.crm.auth.config.JwtTokenProvider;
import com.photoizer.crm.auth.model.RefreshToken;
import com.photoizer.crm.auth.model.TokenBlocklist;
import com.photoizer.crm.auth.repository.RefreshTokenRepository;
import com.photoizer.crm.auth.repository.TokenBlocklistRepository;
import com.photoizer.crm.auth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class RefreshTokenService {

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

    public RefreshToken createRefreshToken(UUID userId, String email, String papel) {
        var tokenValue = jwtTokenProvider.generateRefreshToken(userId, email, papel);
        var expiresAt = jwtTokenProvider.getExpirationFromToken(tokenValue).toInstant();
        var refreshToken = RefreshToken.create(tokenValue, userId, expiresAt);
        return refreshTokenRepository.save(refreshToken);
    }

    public String refreshAccessToken(String refreshTokenValue) {
        if (!jwtTokenProvider.validateToken(refreshTokenValue)) {
            throw new org.springframework.security.authentication.BadCredentialsException("Refresh token inválido");
        }

        if (!jwtTokenProvider.isRefreshToken(refreshTokenValue)) {
            throw new org.springframework.security.authentication.BadCredentialsException("Token não é um refresh token");
        }

        var storedToken = refreshTokenRepository.findByToken(refreshTokenValue)
            .orElseThrow(() -> new org.springframework.security.authentication.BadCredentialsException("Refresh token não encontrado"));

        if (storedToken.isExpired()) {
            refreshTokenRepository.delete(storedToken);
            throw new org.springframework.security.authentication.BadCredentialsException("Refresh token expirado");
        }

        var userId = UUID.fromString(jwtTokenProvider.getUserIdFromToken(refreshTokenValue));
        var email = jwtTokenProvider.getEmailFromToken(refreshTokenValue);
        var papel = jwtTokenProvider.getPapelFromToken(refreshTokenValue);

        var user = userRepository.findById(userId)
            .orElseThrow(() -> new org.springframework.security.authentication.BadCredentialsException("Usuário não encontrado"));

        if (!user.isAtivo()) {
            throw new org.springframework.security.authentication.BadCredentialsException("Usuário inativo");
        }

        return jwtTokenProvider.generateToken(userId, email, papel);
    }

    public void revokeRefreshToken(String refreshTokenValue) {
        refreshTokenRepository.findByToken(refreshTokenValue)
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
}
