package com.photoizer.crm.despesa.model;

import com.photoizer.crm.shared.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

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
    @Column(nullable = false, length = 20)
    private String categoria;

    @NotNull
    @Column(nullable = false)
    private LocalDate data;

    @Column(columnDefinition = "TEXT")
    private String observacao;
}
