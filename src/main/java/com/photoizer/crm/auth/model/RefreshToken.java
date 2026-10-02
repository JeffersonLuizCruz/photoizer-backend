package com.photoizer.crm.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Refresh token persistido apenas como <b>hash SHA-256</b> (achado H1).
 *
 * <p>Cada refresh token pertence a uma <b>família</b> ({@code familyId}). A cada
 * rotação o token usado é marcado ({@code usedAt}) e um substituto é emitido na
 * mesma família. Se um token já consumido/revogado for reapresentado, toda a
 * família é apagada (reuse detection), invalidando a sessão comprometida.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Hash SHA-256 (hex) do valor do JWT — nunca o token em claro. */
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private UUID familyId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant usedAt;

    private Instant revokedAt;

    private RefreshToken(String tokenHash, UUID userId, UUID familyId, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.familyId = familyId;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public static RefreshToken createRoot(String tokenHash, UUID userId, Instant expiresAt) {
        return new RefreshToken(tokenHash, userId, UUID.randomUUID(), expiresAt);
    }

    public static RefreshToken createReplacement(String tokenHash, UUID userId, UUID familyId, Instant expiresAt) {
        return new RefreshToken(tokenHash, userId, familyId, expiresAt);
    }

    public boolean isExpired() {
        return Instant.now().isAfter(this.expiresAt);
    }

    public boolean isUsado() {
        return usedAt != null;
    }

    public boolean isRevogado() {
        return revokedAt != null;
    }

    public void marcarUsado() {
        this.usedAt = Instant.now();
    }

    public void revogar() {
        this.revokedAt = Instant.now();
    }
}
