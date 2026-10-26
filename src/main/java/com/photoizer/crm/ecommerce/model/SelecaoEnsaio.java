package com.photoizer.crm.ecommerce.model;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Snapshot imutavel da selecao enviada pelo cliente. O staff trabalha sobre
 * este snapshot, nunca sobre o estado mutavel de {@code selecionadaPacote}.
 */
@Entity
@Table(name = "selecoes_ensaio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SelecaoEnsaio {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Embedded
    @Builder.Default
    private AuditInfo auditInfo = new AuditInfo();

    @Column(nullable = false)
    private UUID agendamentoId;

    @Column(nullable = false)
    private int numero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusSelecao status;

    @Column(columnDefinition = "TEXT")
    private String observacaoCliente;

    @Column(nullable = false)
    private LocalDateTime enviadaEm;

    private LocalDateTime iniciadaEm;

    private LocalDateTime concluidaEm;

    private LocalDateTime publicadaEm;

    private LocalDateTime originaisBaixadosEm;

    public void iniciarEdicao() {
        status.validarTransicao(StatusSelecao.EM_EDICAO);
        status = StatusSelecao.EM_EDICAO;
        iniciadaEm = LocalDateTime.now();
    }

    public void concluir() {
        status.validarTransicao(StatusSelecao.CONCLUIDA);
        status = StatusSelecao.CONCLUIDA;
        concluidaEm = LocalDateTime.now();
    }

    public void publicar() {
        status.validarTransicao(StatusSelecao.PUBLICADA);
        status = StatusSelecao.PUBLICADA;
        publicadaEm = LocalDateTime.now();
    }

    public void cancelar() {
        status.validarTransicao(StatusSelecao.CANCELADA);
        status = StatusSelecao.CANCELADA;
    }

    public void registrarDownloadOriginais() {
        originaisBaixadosEm = LocalDateTime.now();
    }
}
