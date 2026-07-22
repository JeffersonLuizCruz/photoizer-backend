package com.photoizer.crm.comissao.model;

import com.photoizer.crm.shared.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "indicacoes")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class Indicacao extends BaseEntity {

    @NotNull
    @Column(nullable = false)
    private UUID agendamentoId;

    @Column
    private UUID indicadorId;

    @NotBlank
    @Column(nullable = false, length = 255)
    private String indicadorNome;

    @NotBlank
    @Column(nullable = false, length = 20)
    private String indicadorTelefone;

    @NotBlank
    @Column(nullable = false, length = 20)
    private String origem;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal percentual;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valorReferencia;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valorComissao;

    @NotBlank
    @Column(nullable = false, length = 20)
    private String status;

    @Column
    private LocalDateTime dataPagamento;
}
