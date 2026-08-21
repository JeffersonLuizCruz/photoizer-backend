package com.photoizer.crm.cliente.model;

import com.photoizer.crm.shared.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.PrePersist;
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
@Setter(AccessLevel.PRIVATE)
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

    /**
     * Define dataCadastro automaticamente na primeira persistência.
     * Corrige inconsistência P2 - apenas registro preenchia dataCadastro.
     */
    @PrePersist
    protected void onCreate() {
        if (dataCadastro == null) {
            dataCadastro = LocalDateTime.now();
        }
    }

    /**
     * Atualiza dados do perfil do cliente.
     * Método de domínio que controla quais campos podem ser alterados.
     * Padrão Domain Model - encapsula regras de negócio na entidade.
     */
    public void atualizarPerfil(String nome, String telefone, String email, String cpf, 
                               String cidade, String estado) {
        if (nome != null) this.nome = nome;
        if (telefone != null) this.telefone = telefone;
        if (email != null) this.email = email;
        if (cpf != null) this.cpf = cpf;
        if (cidade != null) this.cidade = cidade;
        if (estado != null) this.estado = estado;
    }

    /**
     * Atualiza dados administrativos do cliente.
     * Método de domínio para uso apenas pelo admin.
     */
    public void atualizarDados(String nome, String telefone, String email, String cpf,
                              String cidade, String estado, OrigemCliente origem, 
                              String observacoes, String preferencias) {
        this.nome = nome;
        this.telefone = telefone;
        this.email = email;
        this.cpf = cpf;
        this.cidade = cidade;
        this.estado = estado;
        this.origem = origem;
        this.observacoes = observacoes;
        this.preferencias = preferencias;
    }

    /**
     * Define hash da senha (uso interno apenas).
     * Método de domínio para controle de acesso à senha.
     */
    public void definirSenhaHash(String senhaHash) {
        this.senhaHash = senhaHash;
    }
}
