package com.photoizer.crm.notificacao.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notificacoes")
@Getter
@Setter
@NoArgsConstructor
public class Notificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String mensagem;

    @Column(nullable = false)
    private boolean lida;

    @Column(length = 500)
    private String link;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Notificacao(UUID userId, String titulo, String mensagem, String link) {
        this.userId = userId;
        this.titulo = titulo;
        this.mensagem = mensagem;
        this.link = link;
        this.lida = false;
        this.createdAt = LocalDateTime.now();
    }
}
