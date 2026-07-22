package com.photoizer.crm.config.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "configuracoes")
@Getter
@Setter
@NoArgsConstructor
public class Configuracao {

    @Id
    @Column(length = 100)
    private String chave;

    @Column(columnDefinition = "TEXT")
    private String valor;
}
