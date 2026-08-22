package com.photoizer.crm.agenda.model;

import com.photoizer.crm.auth.model.Papel;
import com.photoizer.crm.auth.model.User;
import com.photoizer.crm.shared.model.AuditInfo;
import com.photoizer.crm.shared.model.TipoRepasse;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
@Table(name = "agendamento_fotografos", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"agendamento_id", "fotografo_id"})
})
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgendamentoFotografo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Embedded
    @Builder.Default
    private AuditInfo auditInfo = new AuditInfo();

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agendamento_id", nullable = false)
    private Agendamento agendamento;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fotografo_id", nullable = false)
    private User fotografo;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TipoRepasse tipoValor;

    @Positive
    @Column(precision = 5, scale = 2)
    private BigDecimal percentual;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Papel papelParceiro;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valorRepassar;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RepasseStatus status;

    @Column
    private LocalDateTime dataPagamento;

    public void atualizarRepasse(TipoRepasse tipoValor, BigDecimal percentual, BigDecimal valorRepassar) {
        this.tipoValor = tipoValor;
        this.percentual = percentual;
        this.valorRepassar = valorRepassar;
    }

    public void pagar(LocalDateTime momento) {
        this.status = RepasseStatus.PAGO;
        this.dataPagamento = momento;
    }

    public void cancelar() {
        this.status = RepasseStatus.CANCELADO;
        this.dataPagamento = null;
    }
}
