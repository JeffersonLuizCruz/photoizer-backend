package com.photoizer.crm.financeiro.model;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.shared.model.AuditInfo;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Entidade unificada para fotos e vídeos extras.
 *
 * Pattern: Single Table Inheritance (via enum discriminator) —
 * substitui as entidades duplicadas FotoExtra e VideoExtra,
 * que tinham estrutura idêntica exceto pelo nome da tabela.
 * A coluna 'tipo' discrimina entre FOTO e VIDEO.
 *
 * Migration: dados de fotos_extras e videos_extras devem ser migrados
 * para a nova tabela extras_servico com o campo tipo correspondente.
 */
@Entity
@Table(name = "extras_servico")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExtraServico {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Embedded
    @Builder.Default
    private AuditInfo auditInfo = new AuditInfo();

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agendamento_id", nullable = false)
    private Agendamento agendamento;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoExtra tipo;

    @NotNull
    @Positive
    @Column(nullable = false)
    private Integer quantidade;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valorUnitario;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valorTotal;
}
