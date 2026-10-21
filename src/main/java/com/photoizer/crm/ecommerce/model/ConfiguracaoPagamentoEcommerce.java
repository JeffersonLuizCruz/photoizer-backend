package com.photoizer.crm.ecommerce.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Configuracao global de pagamento do e-commerce (linha unica).
 *
 * <p>O PIX configurado aqui e exibido em todas as galerias e pode ser alterado
 * a qualquer momento pelo staff. E independente da chave PIX usada em
 * propostas/contratos.
 */
@Entity
@Table(name = "ecommerce_configuracao_pagamento")
@Getter
@Setter
@NoArgsConstructor
public class ConfiguracaoPagamentoEcommerce {

    public static final short ID_UNICO = 1;

    @Id
    private Short id = ID_UNICO;

    @Column(name = "pix_chave", length = 200)
    private String pixChave;

    @Column(name = "pix_tipo_chave", length = 20)
    private String pixTipoChave;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @PrePersist
    @PreUpdate
    private void atualizarTimestamp() {
        this.updatedAt = LocalDateTime.now();
    }
}
