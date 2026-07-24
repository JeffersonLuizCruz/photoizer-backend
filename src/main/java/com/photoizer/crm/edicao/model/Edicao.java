package com.photoizer.crm.edicao.model;

import com.photoizer.crm.shared.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "edicoes")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class Edicao extends BaseEntity {

    @NotNull
    @Column(nullable = false)
    private UUID agendamentoId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private StatusEdicao status;

    @Column
    private UUID fotografoId;

    @Column
    private UUID editorId;

    @Column
    private LocalDateTime dataEnvioRaw;

    @Column
    private LocalDateTime dataEnvioEditado;

    @Column(columnDefinition = "TEXT")
    private String observacoes;
}
