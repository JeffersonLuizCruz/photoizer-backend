package com.photoizer.crm.despesa.model;

import com.photoizer.crm.shared.model.BaseEntity;
import com.photoizer.crm.shared.model.FormaPagamento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "despesas")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class Despesa extends BaseEntity {

    @NotBlank
    @Column(nullable = false, length = 255)
    private String descricao;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private DespesaCategoria categoriaRef;

    @NotNull
    @Column(nullable = false)
    private LocalDate data;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private FormaPagamento formaPagamento;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusDespesa status;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecorrenciaDespesa recorrencia;

    @Column
    private LocalDate dataProximaGeracao;

    @Column
    private UUID geradaDeId;

    @Column
    private UUID agendamentoId;

    @Column
    private UUID fotografoId;

    @Column
    private LocalDateTime dataPagamento;

    @Size(max = 500)
    @Column(length = 500)
    private String urlComprovante;

    @Column(columnDefinition = "TEXT")
    private String observacao;
}
