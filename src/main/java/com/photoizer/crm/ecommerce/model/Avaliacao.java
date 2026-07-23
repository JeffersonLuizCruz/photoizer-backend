package com.photoizer.crm.ecommerce.model;

import com.photoizer.crm.shared.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Table(name = "avaliacoes")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class Avaliacao extends BaseEntity {

    @NotNull
    @Column(nullable = false)
    private UUID clienteId;

    @Column
    private UUID agendamentoId;

    @Column
    private UUID pacoteId;

    @Min(1)
    @Max(5)
    @Column(nullable = false)
    private int pontuacao;

    @Column(columnDefinition = "TEXT")
    private String comentario;

    @Column
    private boolean depoimento;

    @Column
    private boolean aprovado;
}
