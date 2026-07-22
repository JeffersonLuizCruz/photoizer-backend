package com.photoizer.crm.foto.model;

import com.photoizer.crm.shared.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "fotos_ensaio")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class FotoEnsaio extends BaseEntity {

    @NotNull
    @Column(nullable = false)
    private UUID agendamentoId;

    @NotBlank
    @Column(nullable = false, length = 500)
    private String originalPath;

    @NotBlank
    @Column(nullable = false, length = 500)
    private String watermarkedPath;

    @NotBlank
    @Column(nullable = false, length = 500)
    private String thumbPath;

    @NotBlank
    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false)
    private int ordem;

    @NotBlank
    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private boolean selecionadaPacote;

    @Column
    private LocalDateTime dataDownload;

    @Column
    private UUID compraExtraId;
}
