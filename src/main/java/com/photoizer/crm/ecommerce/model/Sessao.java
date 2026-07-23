package com.photoizer.crm.ecommerce.model;

import com.photoizer.crm.shared.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "sessoes")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class Sessao extends BaseEntity {

    @Column
    private UUID clienteId;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    private String nomeSessao;

    @Column
    private LocalDate dataRealizacao;

    @Size(max = 255)
    @Column(length = 255)
    private String local;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Size(max = 20)
    @Column(length = 20)
    private String status;
}
