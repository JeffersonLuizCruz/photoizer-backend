package com.photoizer.crm.ecommerce.model;

import com.photoizer.crm.shared.model.AuditInfo;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Solicitação de pacote feita no checkout público. É um lead comercial: não
 * representa pagamento nem reserva de agenda.
 */
@Entity
@Table(name = "leads_pacote")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeadPacote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Embedded
    @Builder.Default
    private AuditInfo auditInfo = new AuditInfo();

    @NotNull
    @Column(name = "pacote_id", nullable = false)
    private UUID pacoteId;

    @NotBlank
    @Size(max = 100)
    @Column(name = "pacote_nome", nullable = false, length = 100)
    private String pacoteNome;

    @Column(name = "valor_base", precision = 10, scale = 2)
    private BigDecimal valorBase;

    @Column(name = "preco_foto_extra", precision = 10, scale = 2)
    private BigDecimal precoFotoExtra;

    @NotBlank
    @Size(max = 120)
    @Column(nullable = false, length = 120)
    private String nome;

    @Size(max = 160)
    @Column(length = 160)
    private String email;

    @NotBlank
    @Size(max = 20)
    @Column(nullable = false, length = 20)
    private String telefone;

    @NotBlank
    @Size(max = 20)
    @Column(name = "opcao_entrega", nullable = false, length = 20)
    private String opcaoEntrega;

    @NotBlank
    @Size(max = 20)
    @Column(name = "forma_pagamento", nullable = false, length = 20)
    private String formaPagamento;

    @Min(0)
    @Column(name = "quantidade_extras", nullable = false)
    private Integer quantidadeExtras;

    @Size(max = 2000)
    @Column(length = 2000)
    private String observacao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusLead status;
}
