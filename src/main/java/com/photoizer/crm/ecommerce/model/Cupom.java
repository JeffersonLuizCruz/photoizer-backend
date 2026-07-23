package com.photoizer.crm.ecommerce.model;

import com.photoizer.crm.shared.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "cupons")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class Cupom extends BaseEntity {

    @Size(max = 50)
    @Column(unique = true, nullable = false, length = 50)
    private String codigo;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Size(max = 10)
    @Column(length = 10)
    private String tipoDesconto;

    @Column(precision = 10, scale = 2)
    private BigDecimal valorDesconto;

    @Column(precision = 10, scale = 2)
    private BigDecimal valorMinimoPedido;

    @Column
    private Integer usoLimite;

    @Column
    private Integer usosAtuais;

    @Column
    private LocalDate dataValidade;

    @Column
    private Boolean ativo;

    @Column
    private Boolean usoUnico;
}
