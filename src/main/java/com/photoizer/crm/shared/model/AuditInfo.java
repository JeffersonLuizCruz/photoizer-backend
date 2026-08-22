package com.photoizer.crm.shared.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EntityListeners;

import java.time.LocalDateTime;

/**
 * PATTERN: Value Object / Embeddable
 * Centraliza os campos de auditoria (createdAt, updatedAt) em um único lugar.
 * Motivo: eliminar a herança de BaseEntity (padrão herdado não idiomático)
 * e usar composição em vez de herança.
 *
 * Uso: @Embedded private AuditInfo auditInfo;
 *
 * Usa @EntityListeners (JPA 2.1 spec) em vez de callbacks diretos no @Embeddable,
 * que não é suportado pelo spec (warn HHH90000035 no Hibernate 7.x).
 */
@Embeddable
@EntityListeners(AuditInfoListener.class)
public class AuditInfo {

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public AuditInfo() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public AuditInfo(LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
