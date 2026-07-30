package com.photoizer.crm.agenda.model;

import com.photoizer.crm.shared.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "rascunhos_agendamento", indexes = {
    @Index(columnList = "usuario_id"),
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class RascunhoAgendamento extends BaseEntity {

    @NotNull
    @Column(nullable = false)
    private UUID usuarioId;

    @Column(length = 36)
    private String clienteId;

    @Column(length = 255)
    private String nome;

    @Column(length = 20)
    private String telefone;

    @Column(length = 255)
    private String email;

    @Column(length = 14)
    private String cpf;

    @Column(length = 255)
    private String cidade;

    @Column(length = 2)
    private String estado;

    @Column(length = 20)
    private String origem;

    @Column(length = 36)
    private String pacoteId;

    @Column
    private LocalDate data;

    @Column(length = 5)
    private String hora;

    @Column(length = 255)
    private String localEnsaio;

    @Column(length = 500)
    private String enderecoCompleto;

    @Column(length = 36)
    private String editorId;

    @Column(precision = 10, scale = 2)
    private BigDecimal custoDeslocamento;

    private Boolean repassarDeslocamento;

    private Boolean autorizaUsoImagem;

    @Column(length = 36)
    private String indicadorId;

    @Column(length = 255)
    private String indicadorNome;

    @Column(length = 20)
    private String indicadorTelefone;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @Column(nullable = false)
    private Integer currentStep;

    @Column(length = 255)
    private String comprovanteName;

    @Column(nullable = false)
    private Boolean confirmado;
}
