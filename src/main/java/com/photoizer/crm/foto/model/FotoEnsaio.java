package com.photoizer.crm.foto.model;

import com.photoizer.crm.shared.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import jakarta.persistence.ElementCollection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private StatusFoto status;

    @Column(nullable = false)
    private boolean selecionadaPacote;

    @Column
    private LocalDateTime dataDownload;

    @Column
    private UUID compraExtraId;

    @Size(max = 255)
    @Column(length = 255)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @ElementCollection
    private List<String> tags = new ArrayList<>();

    @Size(max = 50)
    @Column(length = 50)
    private String categoria;

    @Column
    private LocalDate dataSessao;

    @Column(columnDefinition = "TEXT")
    private String metadataExif;

    @Column(nullable = false)
    private boolean destaque;
}
