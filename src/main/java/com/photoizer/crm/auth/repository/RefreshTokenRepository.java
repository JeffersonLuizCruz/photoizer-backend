package com.photoizer.crm.auth.repository;

import com.photoizer.crm.auth.model.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Busca com lock pessimista para serializar rotações concorrentes do mesmo
     * token (evita emitir dois substitutos a partir do mesmo refresh).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefreshToken r where r.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    void deleteByUserId(UUID userId);

    void deleteByFamilyId(UUID familyId);

    long deleteByExpiresAtBefore(Instant limite);
}
