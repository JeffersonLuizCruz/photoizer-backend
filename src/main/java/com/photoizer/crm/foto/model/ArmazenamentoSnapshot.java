package com.photoizer.crm.foto.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Snapshot diário do consumo de armazenamento.
 *
 * <p>A data é a chave primária: coletar duas vezes no mesmo dia atualiza a
 * mesma linha em vez de duplicar. Guarda apenas agregados, sem caminhos de
 * arquivo.
 */
@Entity
@Table(name = "armazenamento_snapshots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArmazenamentoSnapshot {

    @Id
    @Column(name = "data", nullable = false, updatable = false)
    private LocalDate data;

    @Column(name = "bytes_fotos", nullable = false)
    private long bytesFotos;

    @Column(name = "bytes_derivados", nullable = false)
    private long bytesDerivados;

    @Column(name = "bytes_editadas", nullable = false)
    private long bytesEditadas;

    @Column(name = "bytes_documentos", nullable = false)
    private long bytesDocumentos;

    @Column(name = "bytes_propostas", nullable = false)
    private long bytesPropostas;

    @Column(name = "bytes_outros", nullable = false)
    private long bytesOutros;

    @Column(name = "bytes_total", nullable = false)
    private long bytesTotal;

    @Column(name = "volume_total", nullable = false)
    private long volumeTotal;

    @Column(name = "volume_livre", nullable = false)
    private long volumeLivre;

    @Column(name = "total_fotos", nullable = false)
    private int totalFotos;

    @Column(name = "total_galerias", nullable = false)
    private int totalGalerias;

    @Column(name = "galerias_arquivadas", nullable = false)
    private int galeriasArquivadas;

    @Column(name = "fotos_arquivadas", nullable = false)
    private int fotosArquivadas;

    @Column(name = "bytes_liberados_no_dia", nullable = false)
    private long bytesLiberadosNoDia;

    @Column(name = "coletado_em", nullable = false)
    private LocalDateTime coletadoEm;
}
