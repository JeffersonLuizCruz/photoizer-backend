package com.photoizer.crm.edicao.model;

import com.photoizer.crm.shared.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Table(name = "fotos_edicao")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class FotoEdicao extends BaseEntity {

    @NotNull
    @Column(nullable = false)
    private UUID edicaoId;

    @NotBlank
    @Column(nullable = false, length = 500)
    private String rawPath;

    @NotBlank
    @Column(nullable = false, length = 255)
    private String rawFileName;

    @Column(length = 500)
    private String editedPath;

    @Column(length = 255)
    private String editedFileName;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private StatusFotoEdicao status;

    @Column(nullable = false)
    private int ordem;
}
