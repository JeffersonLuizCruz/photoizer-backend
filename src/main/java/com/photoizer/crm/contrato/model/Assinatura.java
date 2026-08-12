package com.photoizer.crm.contrato.model;

import com.photoizer.crm.shared.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "assinaturas_contrato", uniqueConstraints = {
    @UniqueConstraint(columnNames = "contrato_id")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class Assinatura extends BaseEntity {

    @NotNull
    @Column(nullable = false, unique = true)
    private UUID contratoId;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    private String nomeAssinante;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime dataAssinatura;

    @Size(max = 45)
    @Column(length = 45)
    private String ip;

    @Size(max = 64)
    @Column(length = 64)
    private String hash;
}