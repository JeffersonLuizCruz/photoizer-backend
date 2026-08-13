package com.photoizer.crm.ecommerce.model;

import com.photoizer.crm.shared.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * Comentário de clientes sobre uma foto da galeria, permitindo que o
 * fotógrafo/admin veja e responda (conversa por foto).
 */
@Entity
@Table(name = "foto_comentarios")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class FotoComentario extends BaseEntity {

    @NotNull
    @Column(nullable = false)
    private UUID fotoId;

    @NotNull
    @Column(nullable = false)
    private UUID agendamentoId;

    @Size(max = 120)
    @Column(length = 120)
    private String autorNome;

    @NotBlank
    @Column(nullable = false, columnDefinition = "TEXT")
    private String mensagem;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrigemComentario origem;

    @Column(nullable = false)
    private boolean lida;
}