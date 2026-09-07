package com.photoizer.crm.shared.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EntityListeners;

import java.time.LocalDateTime;

/**
 * PATTERN: Value Object / Embeddable
 * Centraliza os campos de auditoria (createdAt, updatedAt, createdBy) em um único lugar.
 * Composição em vez de herança — entidades usam @Embedded private AuditInfo auditInfo.
 *
 * createdBy é populado automaticamente pelo AuditInfoListener via SecurityContextHolder.
 * Em operações sem request HTTP (DataSeeder, schedulers, eventos), assume "SYSTEM".
 */
@Embeddable
@EntityListeners(AuditInfoListener.class)
public class AuditInfo {

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false, updatable = false)
    private String createdBy;

    public AuditInfo() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.createdBy = "SYSTEM";
    }

    public AuditInfo(LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.createdBy = "SYSTEM";
    }

    public AuditInfo(LocalDateTime createdAt, LocalDateTime updatedAt, String createdBy) {
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}
