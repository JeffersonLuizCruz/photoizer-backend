package com.photoizer.crm.pacote.model;

import com.photoizer.crm.shared.model.AuditInfo;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "pacotes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pacote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Embedded
    @Builder.Default
    private AuditInfo auditInfo = new AuditInfo();

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

    @NotNull
    @PositiveOrZero
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precoFotoExtra;

    @Size(max = 500)
    @Column(length = 500)
    private String imagemCapa;

    @Column(columnDefinition = "TEXT")
    private String beneficios;

    @Size(max = 50)
    @Column(length = 50)
    private String duracaoEstimada;

    @NotNull
    @Column(nullable = false)
    private Boolean bloqueiaDiaInteiro;

    @NotNull
    @Column(nullable = false)
    private Boolean ativo;

    @PositiveOrZero
    @Column
    private Integer diasParaEntrega;
}
