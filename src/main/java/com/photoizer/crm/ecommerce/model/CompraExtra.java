package com.photoizer.crm.ecommerce.model;

import com.photoizer.crm.shared.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "compras_extras")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class CompraExtra extends BaseEntity {

    @NotNull
    @Column(nullable = false)
    private UUID agendamentoId;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valorTotal;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusCompraExtra status;

    @Column(length = 500)
    private String urlComprovante;

    @Column
    private LocalDateTime dataPagamento;

    @Column
    private Integer quantidadeFotos;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MetodoPagamento metodoPagamento;

    @Column(columnDefinition = "TEXT")
    private String observacao;
}
