package com.photoizer.crm.pacote.model;

import com.photoizer.crm.agenda.model.Usuario;
import com.photoizer.crm.shared.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Table(name = "pacotes")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class Pacote extends BaseEntity {

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String nome;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @NotNull
    @Positive
    @Column(nullable = false)
    private Integer quantidadeFotos;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false)
    private Integer quantidadeVideos;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valorBase;

    @Size(max = 50)
    @Column(length = 50)
    private String duracaoEstimada;

    @NotNull
    @Column(nullable = false)
    private Boolean bloqueiaDiaInteiro;

    @NotNull
    @Column(nullable = false)
    private Boolean ativo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fotografo_id")
    private Usuario fotografo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "editor_responsavel_id")
    private Usuario editorResponsavel;

    @Positive
    @Column
    private Integer diasParaEntrega;
}
