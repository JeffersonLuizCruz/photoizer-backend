package com.photoizer.crm.shared.model;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.time.LocalDateTime;

/**
 * JPA EntityListener para Auditable embeddable.
 * Usa @EntityListeners (JPA 2.1 spec) em vez de callbacks diretos no @Embeddable,
 * que não é suportado pelo spec e gera warning HHH90000035 no Hibernate 7.x.
 */
public class AuditInfoListener {

    @PrePersist
    void onCreate(AuditInfo auditInfo) {
        auditInfo.setCreatedAt(LocalDateTime.now());
        auditInfo.setUpdatedAt(LocalDateTime.now());
    }

    @PreUpdate
    void onUpdate(AuditInfo auditInfo) {
        auditInfo.setUpdatedAt(LocalDateTime.now());
    }
}
