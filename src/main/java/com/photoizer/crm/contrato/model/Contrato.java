package com.photoizer.crm.contrato.model;

import com.photoizer.crm.shared.model.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "contratos", indexes = {
    @Index(columnList = "status"),
    @Index(columnList = "token_hash")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class Contrato extends BaseEntity {

    @Size(max = 64)
    @Column(unique = true, length = 64)
    private String tokenHash;

    @Size(max = 128)
    @Column(length = 128)
    private String token;

    @Column
    private LocalDateTime publicadoEm;

    @Column
    private LocalDateTime tokenExpiracao;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private StatusContrato status;

    @NotNull
    @Column(nullable = false)
    private UUID pacoteId;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String pacoteNome;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valorPacote;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precoFotoExtra;

    @Column
    private UUID editorId;

    @Column
    private UUID fotografoId;

    @PositiveOrZero
    @Column(precision = 10, scale = 2)
    private BigDecimal valorRepassarFotografo;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime dataHoraEnsaio;

    @NotNull
    @Positive
    @Column(nullable = false)
    private Integer duracaoMinutos;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    private String localEnsaio;

    @Size(max = 500)
    @Column(length = 500)
    private String enderecoCompleto;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal custoDeslocamento;

    @NotNull
    @Column(nullable = false)
    private Boolean repassarDeslocamento;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal taxaDeslocamento;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal percentualEntrada;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valorTotal;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valorEntradaExigido;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valorRestante;

    @Column
    private UUID clienteId;

    @Column
    private String clienteNome;

    @Column
    private String clienteTelefone;

    @Column
    private String clienteEmail;

    @Column
    private String clienteCpf;

    @Column
    private String clienteCidade;

    @Column
    private String clienteEstado;

    @Column
    private Boolean autorizaUsoImagem;

    @Size(max = 500)
    @Column(length = 500)
    private String urlComprovanteEntrada;

    @Column(columnDefinition = "TEXT")
    private String snapshotJson;

    @Size(max = 64)
    @Column(length = 64)
    private String snapshotHash;

    @Size(max = 500)
    @Column(length = 500)
    private String urlPdf;

    @Column
    private LocalDateTime dataPagamentoConfirmado;

    @Column
    private LocalDateTime dataAssinatura;

    @Column
    private LocalDateTime dataAprovacao;

    @Column
    private LocalDateTime dataDevolucao;

    @Size(max = 30)
    @Column(length = 30)
    private String tipoMotivoDevolucao;

    @Column(columnDefinition = "TEXT")
    private String motivoDevolucao;

    @Column
    private UUID indicadorId;

    @Size(max = 255)
    @Column(length = 255)
    private String indicadorNome;

    @Size(max = 20)
    @Column(length = 20)
    private String indicadorTelefone;

    @Column
    private UUID agendamentoId;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @OneToMany(mappedBy = "contrato", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ContratoFotografo> fotografos;

    public void addFotografo(ContratoFotografo link) {
        if (this.fotografos == null) this.fotografos = new ArrayList<>();
        this.fotografos.add(link);
        link.setContrato(this);
    }
}