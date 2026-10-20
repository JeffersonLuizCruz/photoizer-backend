package com.photoizer.crm.bloqueio.model;

import com.photoizer.crm.shared.model.AuditInfo;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Compromisso/indisponibilidade criado pelo staff para impedir novos
 * agendamentos em um intervalo de tempo.
 *
 * profissionalId nulo => bloqueio global (toda a equipe).
 * profissionalId preenchido => bloqueio apenas do profissional informado.
 * Periodo em intervalo semiaberto [inicio, fim).
 */
@Entity
@Table(name = "bloqueios_agenda")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BloqueioAgenda {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 120)
    @Setter
    private String titulo;

    @Column(length = 500)
    @Setter
    private String descricao;

    @Column(nullable = false)
    @Setter
    private LocalDateTime inicio;

    @Column(nullable = false)
    @Setter
    private LocalDateTime fim;

    @Column
    @Setter
    private UUID profissionalId;

    @Column(nullable = false, updatable = false)
    private UUID criadoPorId;

    @Column
    private LocalDateTime canceladoEm;

    @Column
    private UUID canceladoPorId;

    @Column(nullable = false)
    @Builder.Default
    private boolean ativo = true;

    @Embedded
    @Builder.Default
    private AuditInfo auditInfo = new AuditInfo();

    public boolean isGlobal() {
        return profissionalId == null;
    }

    public boolean aplicaA(UUID fotografoId) {
        return isGlobal() || (fotografoId != null && fotografoId.equals(profissionalId));
    }

    public void cancelar(UUID solicitanteId) {
        this.ativo = false;
        this.canceladoEm = LocalDateTime.now();
        this.canceladoPorId = solicitanteId;
    }

    public void reativar() {
        this.ativo = true;
        this.canceladoEm = null;
        this.canceladoPorId = null;
    }
}
