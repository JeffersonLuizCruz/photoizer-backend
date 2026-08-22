package com.photoizer.crm.comissao.model;

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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "indicacoes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Indicacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Embedded
    @Builder.Default
    private AuditInfo auditInfo = new AuditInfo();

    @NotNull
    @Column(nullable = false)
    private UUID agendamentoId;

    @Column
    private UUID indicadorId;

    @NotBlank
    @Column(nullable = false, length = 255)
    private String indicadorNome;

    @NotBlank
    @Column(nullable = false, length = 20)
    private String indicadorTelefone;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigemIndicacao origem;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal percentual;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valorReferencia;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valorComissao;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusIndicacao status;

    @Column
    private LocalDateTime dataPagamento;

    public void pagar() {
        if (!status.podePagar()) {
            throw new IllegalStateException(
                "Não é possível pagar comissão com status: " + status);
        }
        this.status = StatusIndicacao.PAGA;
        this.dataPagamento = LocalDateTime.now();
    }

    public void cancelar() {
        if (!status.podeCancelar()) {
            throw new IllegalStateException(
                "Não é possível cancelar comissão com status: " + status);
        }
        this.status = StatusIndicacao.CANCELADA;
    }
}
