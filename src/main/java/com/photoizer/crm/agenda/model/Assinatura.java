package com.photoizer.crm.agenda.model;

import com.photoizer.crm.shared.model.AuditInfo;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Registro de assinatura digital do termo de prestação de serviços,
 * vinculado 1:1 ao agendamento. Guarda a prova técnica (IP + dados do
 * aparelho) e o hash do snapshot imutável assinado.
 */
@Entity
@Table(name = "assinaturas", uniqueConstraints = {
    @UniqueConstraint(columnNames = "agendamento_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Assinatura {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Embedded
    @Builder.Default
    private AuditInfo auditInfo = new AuditInfo();

    @NotNull
    @Column(name = "agendamento_id", nullable = false, unique = true)
    private UUID agendamentoId;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    private String nomeAssinante;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime dataAssinatura;

    @Size(max = 45)
    @Column(length = 45)
    private String ip;

    @Size(max = 64)
    @Column(length = 64)
    private String hash;

    @Size(max = 500)
    @Column(length = 500)
    private String userAgent;

    @Size(max = 150)
    @Column(length = 150)
    private String plataforma;

    @Size(max = 100)
    @Column(length = 100)
    private String fusoHorario;

    @Size(max = 500)
    @Column(length = 500)
    private String urlAssinaturaImagem;
}
