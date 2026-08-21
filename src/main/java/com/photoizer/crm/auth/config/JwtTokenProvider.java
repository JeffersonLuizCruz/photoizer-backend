package com.photoizer.crm.auth.config;

import com.photoizer.crm.shared.auth.TokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * Provedor de tokens JWT.
 * Implementa TokenService para permitir uso por outros módulos sem acoplamento direto.
 * Padrão Dependency Inversion - implementa abstração do módulo shared.
 */
@Component
public class JwtTokenProvider implements TokenService {

    private final SecretKey secretKey;
    private final long expiration;
    private final long refreshExpiration;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration}") long expiration,
            @Value("${app.jwt.refresh-expiration}") long refreshExpiration) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
        this.refreshExpiration = refreshExpiration;
    }

    @Override
    public String generateToken(UUID userId, String email, String papel) {
        var now = new Date();
        var jti = UUID.randomUUID().toString();
        return Jwts.builder()
            .id(jti)
            .subject(userId.toString())
            .claim("email", email)
            .claim("papel", papel)
            .issuedAt(now)
            .expiration(new Date(now.getTime() + expiration))
            .signWith(secretKey)
            .compact();
    }

    public String generateRefreshToken(UUID userId, String email, String papel) {
        var now = new Date();
        var jti = UUID.randomUUID().toString();
        return Jwts.builder()
            .id(jti)
            .subject(userId.toString())
            .claim("email", email)
            .claim("papel", papel)
            .claim("type", "refresh")
            .issuedAt(now)
            .expiration(new Date(now.getTime() + refreshExpiration))
            .signWith(secretKey)
            .compact();
    }

    public String getJtiFromToken(String token) {
        return getClaims(token).getId();
    }

    public String getUserIdFromToken(String token) {
        return getClaims(token).getSubject();
    }

    public String getPapelFromToken(String token) {
        return getClaims(token).get("papel", String.class);
    }

    public String getEmailFromToken(String token) {
        return getClaims(token).get("email", String.class);
    }

    public Date getExpirationFromToken(String token) {
        return getClaims(token).getExpiration();
    }

    public boolean isRefreshToken(String token) {
        var type = getClaims(token).get("type", String.class);
        return "refresh".equals(type);
    }

    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
