package com.photoizer.crm.agenda.model;

import com.photoizer.crm.auth.model.User;
import com.photoizer.crm.cliente.model.Cliente;
import com.photoizer.crm.pacote.model.Pacote;
import com.photoizer.crm.shared.model.AuditInfo;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "agendamentos", indexes = {
    @Index(columnList = "data_hora_ensaio"),
    @Index(columnList = "status"),
    @Index(columnList = "cliente_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Agendamento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Embedded
    @Builder.Default
    private AuditInfo auditInfo = new AuditInfo();

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pacote_id", nullable = false)
    private Pacote pacote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "editor_id")
    private User editor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fotografo_id")
    private User fotografo;

    @PositiveOrZero
    @Column(name = "valor_partilha_fotografo", precision = 10, scale = 2)
    private BigDecimal valorPartilhaGlobal;

    @PositiveOrZero
    @Column(precision = 10, scale = 2)
    private BigDecimal valorLucroCrm;

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
    private BigDecimal valorEntradaPago;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valorRestante;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valorExtras;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal taxaDeslocamento;

    @PositiveOrZero
    @Column(precision = 10, scale = 2)
    private BigDecimal custoDeslocamento;

    @Column
    private Boolean repassarDeslocamento;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valorTotalFinal;

    @PositiveOrZero
    @Column(precision = 5, scale = 2)
    private BigDecimal percentualEntrada;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private StatusAgendamento status;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime dataConfirmacao;

    @Column
    private LocalDateTime dataRealizacao;

    @Column
    private LocalDateTime dataEnvioSelecao;

    @Column
    private LocalDateTime dataEntregaFinal;

    @Column
    private LocalDateTime dataFinalizacao;

    @NotBlank
    @Size(max = 500)
    @Column(nullable = false, length = 500)
    private String urlComprovanteEntrada;

    @Size(max = 500)
    @Column(length = 500)
    private String urlComprovanteFinal;

    @NotNull
    @Column(nullable = false)
    private Boolean autorizaUsoImagem;

    @Column(columnDefinition = "TEXT")
    private String clausulasPersonalizadas;

    @NotNull
    @Column(nullable = false)
    private Boolean contratoGerado;

    @NotNull
    @Column(nullable = false)
    private Boolean ensaioDestaque;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @Column(unique = true, nullable = false)
    private UUID tokenGaleria;

    @Column
    private LocalDateTime tokenExpiracao;

    public void transicionarPara(StatusAgendamento novoStatus) {
        this.status = novoStatus;
        if (novoStatus == StatusAgendamento.REALIZADO) {
            this.dataRealizacao = LocalDateTime.now();
        }
    }

    public void reagendar(LocalDateTime novaDataHora, int novaDuracaoMinutos) {
        this.dataHoraEnsaio = novaDataHora;
        this.duracaoMinutos = novaDuracaoMinutos;
        this.status = StatusAgendamento.CONFIRMADO;
        this.dataConfirmacao = LocalDateTime.now();
    }

    public void aplicarPagamentoFinal(String urlComprovanteFinal) {
        this.urlComprovanteFinal = urlComprovanteFinal;
        this.valorRestante = BigDecimal.ZERO;
        this.valorEntradaPago = this.valorTotalFinal;
        this.status = StatusAgendamento.EM_EDICAO;
        this.dataEnvioSelecao = LocalDateTime.now();
    }

    public void alternarDestaque() {
        this.ensaioDestaque = !this.ensaioDestaque;
    }
}
