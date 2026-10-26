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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Item do snapshot de selecao. Guarda numero e nome do arquivo no momento do
 * envio para que o staff continue vendo a mesma referencia mesmo se a foto for
 * renomeada depois.
 */
@Entity
@Table(name = "selecao_ensaio_itens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SelecaoEnsaioItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Embedded
    @Builder.Default
    private AuditInfo auditInfo = new AuditInfo();

    @Column(nullable = false)
    private UUID selecaoId;

    @Column(nullable = false)
    private UUID fotoId;

    @Column(name = "numero_galeria", nullable = false)
    private int numeroGaleria;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoItemSelecao tipo;

    @Column(nullable = false)
    private int ordem;

    @Column(length = 255)
    private String nomeArquivo;

    @Column(precision = 10, scale = 2)
    private BigDecimal valorUnitario;

    @Column(nullable = false)
    private boolean editada;

    private LocalDateTime editadaEm;

    public void marcarEditada() {
        if (!editada) {
            editada = true;
            editadaEm = LocalDateTime.now();
        }
    }
}
