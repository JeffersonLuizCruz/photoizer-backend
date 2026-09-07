package com.photoizer.crm.shared.model;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;

/**
 * JPA EntityListener para Auditable embeddable.
 *
 * @PrePersist: popula createdAt, updatedAt e createdBy.
 * @PreUpdate: popula apenas updatedAt.
 *
 * createdBy é resolvido via SecurityContextHolder (userId UUID string).
 * Fallback "SYSTEM" para operações sem request HTTP (DataSeeder, schedulers, eventos).
 *
 * NOTA: EntityListeners são gerenciados pelo JPA (não pelo Spring),
 * portanto não é possível usar @Autowired aqui. A chamada direta
 * ao SecurityContextHolder é a abordagem correta.
 */
public class AuditInfoListener {

    @PrePersist
    void onCreate(AuditInfo auditInfo) {
        auditInfo.setCreatedAt(LocalDateTime.now());
        auditInfo.setUpdatedAt(LocalDateTime.now());
        auditInfo.setCreatedBy(resolveCurrentUser());
    }

    @PreUpdate
    void onUpdate(AuditInfo auditInfo) {
        auditInfo.setUpdatedAt(LocalDateTime.now());
    }

    private String resolveCurrentUser() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal())) {
                return auth.getName();
            }
        } catch (Exception ignored) {
            // SecurityContextHolder pode não estar disponível em contexto de teste ou async
        }
        return "SYSTEM";
    }
}
