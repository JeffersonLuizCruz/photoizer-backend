package com.photoizer.crm.cliente.model;

import com.photoizer.crm.shared.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Table(name = "clientes", uniqueConstraints = {
    @UniqueConstraint(columnNames = "telefone"),
    @UniqueConstraint(columnNames = "cpf")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class Cliente extends BaseEntity {

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    private String nome;

    @NotBlank
    @Size(max = 20)
    @Column(nullable = false, unique = true, length = 20)
    private String telefone;

    @Email
    @Size(max = 255)
    @Column(length = 255)
    private String email;

    @Pattern(regexp = "\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}")
    @Size(max = 14)
    @Column(unique = true, length = 14)
    private String cpf;

    @Size(max = 100)
    @Column(length = 100)
    private String cidade;

    @Size(max = 2)
    @Column(length = 2)
    private String estado;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigemCliente origem;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @Size(max = 255)
    @Column(length = 255)
    private String senhaHash;

    @Column
    private LocalDateTime dataCadastro;

    @Column(columnDefinition = "TEXT")
    private String preferencias;
}
