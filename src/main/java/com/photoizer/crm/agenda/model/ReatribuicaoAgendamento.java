package com.photoizer.crm.agenda.model;

import com.photoizer.crm.auth.model.User;
import com.photoizer.crm.shared.model.AuditInfo;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Histórico de transferências do fotógrafo responsável de um agendamento.
 *
 * PATTERN: Domain Event Log — registra quem transferiu, de quem para quem e o motivo,
 * permitindo auditoria e exibição na linha do tempo do agendamento.
 */
@Entity
@Table(name = "agendamento_reatribuicoes", indexes = {
    @Index(columnList = "agendamento_id")
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReatribuicaoAgendamento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Embedded
    @Builder.Default
    private AuditInfo auditInfo = new AuditInfo();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agendamento_id", nullable = false)
    private Agendamento agendamento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fotografo_anterior_id")
    private User fotografoAnterior;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "novo_fotografo_id", nullable = false)
    private User novoFotografo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solicitante_id")
    private User solicitante;

    @Column(columnDefinition = "TEXT")
    private String motivo;

    public static ReatribuicaoAgendamento registrar(Agendamento agendamento, User anterior,
                                                    User novo, User solicitante, String motivo) {
        return ReatribuicaoAgendamento.builder()
            .agendamento(agendamento)
            .fotografoAnterior(anterior)
            .novoFotografo(novo)
            .solicitante(solicitante)
            .motivo(motivo)
            .build();
    }
}
