package com.photoizer.crm.ecommerce.model;

import com.photoizer.crm.shared.model.AuditInfo;
import com.photoizer.crm.foto.model.VersaoFoto;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Configuracao comercial da galeria de um ensaio (1:1 com Agendamento).
 * Mantida no modulo ecommerce para nao acoplar o agregado Agenda a fase de
 * venda/selecao.
 */
@Entity
@Table(name = "galerias_ensaio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GaleriaEnsaio {

    @Id
    @Column(name = "agendamento_id", nullable = false, updatable = false)
    private UUID agendamentoId;

    @Embedded
    @Builder.Default
    private AuditInfo auditInfo = new AuditInfo();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModoGaleria modo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FaseGaleria fase;

    @Column(name = "selecao_atual_id")
    private UUID selecaoAtualId;

    @Column(name = "capa_foto_id")
    private UUID capaFotoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "capa_versao", length = 20)
    private VersaoFoto capaVersao;

    /**
     * Texto customizado do hero. Nulo ou vazio significa usar o nome do
     * cliente, preservando o comportamento anterior.
     */
    @Column(name = "titulo_hero", length = 120)
    private String tituloHero;

    /**
     * Momento da primeira publicacao das fotos da galeria. Nulo enquanto o
     * staff ainda nao publicou nenhuma foto.
     */
    @Column(name = "publicada_em")
    private LocalDateTime publicadaEm;

    public boolean permiteSelecionar() {
        return modo == ModoGaleria.DIRETO || fase.permiteSelecionar();
    }

    public boolean permiteComercializar() {
        return modo == ModoGaleria.DIRETO || fase.permiteComercializar();
    }

    /** Mantem a data da primeira publicacao; publicacoes posteriores nao a alteram. */
    public void registrarPublicacao(LocalDateTime publicadoEm) {
        if (publicadoEm == null) {
            return;
        }
        if (this.publicadaEm == null || publicadoEm.isBefore(this.publicadaEm)) {
            this.publicadaEm = publicadoEm;
        }
    }

    public void iniciarSelecao(UUID selecaoId) {
        this.selecaoAtualId = selecaoId;
        this.fase = FaseGaleria.SELECAO_ENVIADA;
    }

    public void iniciarEdicao() {
        this.fase = FaseGaleria.EM_EDICAO;
    }

    public void publicarEdicao() {
        this.fase = FaseGaleria.COMERCIALIZACAO;
    }

    /**
     * Aprova a selecao sem edicao: o snapshot fica congelado e a galeria passa
     * direto para comercializacao, entregando os arquivos originais.
     */
    public void aprovarSemEdicao() {
        this.fase = FaseGaleria.COMERCIALIZACAO;
    }

    public void reabrirSelecao() {
        this.selecaoAtualId = null;
        this.fase = FaseGaleria.SELECAO;
    }
}
