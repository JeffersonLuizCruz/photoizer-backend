package com.photoizer.crm.ecommerce.model;

import com.photoizer.crm.shared.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "pedidos")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class Pedido extends BaseEntity {

    @NotNull
    @Column(nullable = false)
    private UUID clienteId;

    @NotNull
    @Column(nullable = false)
    private UUID pacoteId;

    @Column
    private UUID agendamentoId;

    @Column(columnDefinition = "TEXT")
    private String fotosSelecionadasIds;

    @Column(columnDefinition = "TEXT")
    private String fotosExtrasIds;

    @Column(precision = 10, scale = 2)
    private BigDecimal subtotalPacote;

    @Column(precision = 10, scale = 2)
    private BigDecimal subtotalExtras;

    @Column(precision = 10, scale = 2)
    private BigDecimal taxaEntrega;

    @Column(precision = 10, scale = 2)
    private BigDecimal desconto;

    @Column(precision = 10, scale = 2)
    private BigDecimal total;

    @Size(max = 30)
    @Column(length = 30)
    private String status;

    @Size(max = 20)
    @Column(length = 20)
    private String formaPagamento;

    @Size(max = 20)
    @Column(length = 20)
    private String opcaoEntrega;

    @Column(unique = true, nullable = false)
    private UUID tokenGaleria;

    @Column
    private LocalDateTime dataPedido;

    @Column
    private LocalDateTime dataConclusao;
}
