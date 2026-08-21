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

@Entity
@Table(name = "token_blocklist")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TokenBlocklist {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String jti;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant blockedAt;

    private TokenBlocklist(String jti, Instant expiresAt) {
        this.jti = jti;
        this.expiresAt = expiresAt;
        this.blockedAt = Instant.now();
    }

    public static TokenBlocklist create(String jti, Instant expiresAt) {
        return new TokenBlocklist(jti, expiresAt);
    }
}
