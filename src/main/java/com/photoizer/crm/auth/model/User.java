package com.photoizer.crm.auth.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.photoizer.crm.shared.model.AuditInfo;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    @Setter
    private String email;

    @JsonIgnore
    @Column(nullable = false)
    @Setter
    private String password;

    @Column(nullable = false, length = 100)
    @Setter
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Setter
    private Papel papel;

    @Column(length = 20)
    @Setter
    private String telefone;

    @Column(nullable = false)
    @Setter
    private boolean ativo = true;

    @Embedded
    @Setter
    private AuditInfo auditInfo = new AuditInfo();

    public User(String email, String password, String nome, Papel papel) {
        this.email = email;
        this.password = password;
        this.nome = nome;
        this.papel = papel;
    }
}
