package com.photoizer.crm.notificacao.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidade de notificação do sistema.
 *
 * Nota: Não estende BaseEntity — tem id/createdAt próprios sem updatedAt/createdBy.
 * Única entidade fora do padrão (documentado no AGENTS.md).
 */
@Entity
@Table(name = "notificacoes")
@Getter
@Setter
public class Notificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(nullable = false)
    private UUID userId;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String titulo;

    @NotBlank
    @Column(nullable = false, length = 500)
    private String mensagem;

    @Column(length = 255)
    private String link;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoNotificacao tipo;

    @Column(nullable = false)
    private boolean lida = false;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Notificacao() {}

    public Notificacao(UUID userId, String titulo, String mensagem, String link, TipoNotificacao tipo) {
        this.userId = userId;
        this.titulo = titulo;
        this.mensagem = mensagem;
        this.link = link;
        this.tipo = tipo;
        this.lida = false;
        this.createdAt = LocalDateTime.now();
    }
}