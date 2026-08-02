package com.photoizer.crm.indicador.model;

import com.photoizer.crm.shared.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Table(name = "indicadores", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"nome", "telefone"})
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class Indicador extends BaseEntity {

    @NotBlank
    @Column(nullable = false, length = 255)
    private String nome;

    @NotBlank
    @Column(nullable = false, length = 20)
    private String telefone;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @DecimalMin("0.00")
    @DecimalMax("100.00")
    @Column(precision = 5, scale = 2)
    private BigDecimal percentualComissao;
}
