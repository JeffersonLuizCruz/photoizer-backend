package com.photoizer.crm.agenda.model;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.photoizer.crm.auth.model.User;
import com.photoizer.crm.cliente.model.Cliente;
import com.photoizer.crm.pacote.model.Pacote;
import com.photoizer.crm.shared.exception.BadRequestException;
import com.photoizer.crm.shared.model.AuditInfo;
import com.photoizer.crm.shared.model.FormaPagamento;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
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
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 40)
    private StatusAgendamento status;

    @Column
    private LocalDateTime dataConfirmacao;

    @Column
    private LocalDateTime dataRealizacao;

    @Column
    private LocalDateTime dataFinalizacao;

    @Size(max = 500)
    @Column(length = 500)
    private String urlComprovanteEntrada;

    @Size(max = 500)
    @Column(length = 500)
    private String urlComprovanteFinal;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private FormaPagamento formaPagamentoFinal;

    @NotNull
    @Column(nullable = false)
    private Boolean autorizaUsoImagem;

    @Column(columnDefinition = "TEXT")
    private String clausulasPersonalizadas;

    @NotNull
    @Column(nullable = false)
    private Boolean ensaioDestaque;

    @Size(max = 128)
    @Column(length = 128)
    private String tokenProposta;

    @Size(max = 64)
    @Column(unique = true, length = 64)
    private String tokenPropostaHash;

    @Column
    private LocalDateTime tokenPropostaExpiracao;

    @Column
    private LocalDateTime dataEnvioProposta;

    @Column
    private LocalDateTime dataAssinatura;

    @Size(max = 64)
    @Column(length = 64)
    private String assinanteNome;

    @Column(columnDefinition = "TEXT")
    private String snapshotJson;

    @Size(max = 64)
    @Column(length = 64)
    private String snapshotHash;

    @Size(max = 500)
    @Column(length = 500)
    private String urlPdfAssinatura;

    @Size(max = 500)
    @Column(length = 500)
    private String urlAssinaturaImagem;

    @Size(max = 500)
    @Column(length = 500)
    private String motivoRecusa;

    @Size(max = 150)
    @Column(length = 150)
    private String recusadoPor;

    @Column
    private LocalDateTime dataRecusa;

    @Column
    private UUID indicadorId;

    @Size(max = 150)
    @Column(length = 150)
    private String indicadorNome;

    @Size(max = 30)
    @Column(length = 30)
    private String indicadorTelefone;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @Column(unique = true, nullable = false)
    private UUID tokenGaleria;

    @Column
    private LocalDateTime tokenExpiracao;

    public void transicionarPara(StatusAgendamento novoStatus) {
        this.status.validarTransicao(novoStatus);
        this.status = novoStatus;
        if (novoStatus == StatusAgendamento.REALIZADO) {
            this.dataRealizacao = LocalDateTime.now();
        }
    }

    public String nomeCliente() {
        return cliente != null && cliente.getNome() != null ? cliente.getNome() : "Pré-reserva";
    }

    /** Gera/regera o token público do link de assinatura da proposta. */
    public void definirTokenProposta(String token, String tokenHash, LocalDateTime expiracao) {
        this.tokenProposta = token;
        this.tokenPropostaHash = tokenHash;
        this.tokenPropostaExpiracao = expiracao;
    }

    /** Registra o momento em que a proposta foi enviada ao cliente. */
    public void registrarEnvioProposta() {
        this.dataEnvioProposta = LocalDateTime.now();
    }

    /**
     * Assinatura pública da proposta: vincula o cliente (auto-cadastro), registra
     * autorização de imagem, comprovante, assinatura e snapshot imutável,
     * transitando PRE_RESERVA -> AGUARDANDO_APROVACAO.
     */
    public void assinarProposta(Cliente cliente, String autorizaUsoImagem,
                                String urlComprovante, String assinanteNome,
                                String snapshotJson, String snapshotHash,
                                String urlPdf, String urlAssinaturaImagem) {
        this.status.validarTransicao(StatusAgendamento.AGUARDANDO_APROVACAO);
        this.cliente = cliente;
        this.autorizaUsoImagem = "true".equalsIgnoreCase(autorizaUsoImagem);
        this.urlComprovanteEntrada = urlComprovante;
        this.assinanteNome = assinanteNome;
        this.snapshotJson = snapshotJson;
        this.snapshotHash = snapshotHash;
        this.urlPdfAssinatura = urlPdf;
        this.urlAssinaturaImagem = urlAssinaturaImagem;
        this.dataAssinatura = LocalDateTime.now();
        this.status = StatusAgendamento.AGUARDANDO_APROVACAO;
    }

    public void confirmarPagamento() {
        this.status.validarTransicao(StatusAgendamento.PAGAMENTO_CONFIRMADO);
        this.status = StatusAgendamento.PAGAMENTO_CONFIRMADO;
    }

    public void aprovar() {
        this.status.validarTransicao(StatusAgendamento.CONFIRMADO);
        this.status = StatusAgendamento.CONFIRMADO;
        this.dataConfirmacao = LocalDateTime.now();
        this.valorEntradaPago = this.valorEntradaExigido;
        this.valorRestante = this.valorTotalFinal.subtract(this.valorEntradaPago).max(BigDecimal.ZERO);
    }

    /**
     * Recusa a proposta: cancela o agendamento registrando o motivo.
     * Permitido apenas enquanto a proposta está em análise (pré-reserva).
     * O autor e a data são gravados pelo service (contexto de segurança).
     */
    public void recusar(String motivo) {
        if (!this.status.isPreReserva()) {
            throw new BadRequestException("Somente propostas em análise podem ser recusadas.");
        }
        transicionarPara(StatusAgendamento.CANCELADO);
        this.motivoRecusa = motivo;
        this.dataRecusa = LocalDateTime.now();
    }

    /**
     * Normaliza os campos de recusa: um motivo só existe quando o status é
     * CANCELADO. Cancelamentos genéricos (via transição de status) não devem
     * carregar motivo de recusa.
     */
    @PrePersist
    @PreUpdate
    void normalizarRecusa() {
        if (this.status != StatusAgendamento.CANCELADO) {
            this.motivoRecusa = null;
            this.recusadoPor = null;
            this.dataRecusa = null;
        }
    }

    public void reagendar(LocalDateTime novaDataHora, int novaDuracaoMinutos) {
        this.dataHoraEnsaio = novaDataHora;
        this.duracaoMinutos = novaDuracaoMinutos;
        this.status = StatusAgendamento.CONFIRMADO;
        this.dataConfirmacao = LocalDateTime.now();
    }

    public void reatribuirFotografo(User novoResponsavel) {
        this.fotografo = novoResponsavel;
    }

    public void aplicarPagamentoFinal(String urlComprovanteFinal, FormaPagamento formaPagamento) {
        this.urlComprovanteFinal = urlComprovanteFinal;
        this.formaPagamentoFinal = formaPagamento;
        this.valorRestante = BigDecimal.ZERO;
        this.valorEntradaPago = this.valorTotalFinal;
        transicionarPara(StatusAgendamento.EM_EDICAO);
    }

    public void alternarDestaque() {
        this.ensaioDestaque = !this.ensaioDestaque;
    }

    /**
     * Registra um pagamento parcial ou total no agendamento.
     * Atualiza valorEntradaPago, recalcula valorRestante e,
     * se quitado, transiciona para AGUARDANDO_PAGAMENTO_FINAL.
     *
     * Pattern: Domain Method — encapsula regra de negócio de pagamento no aggregate root.
     */
    public void registrarPagamento(BigDecimal valorPago) {
        if (valorPago == null || valorPago.signum() <= 0) return;
        this.valorEntradaPago = this.valorEntradaPago.add(valorPago);
        this.valorRestante = this.valorRestante.subtract(valorPago).max(BigDecimal.ZERO);
        if (this.valorRestante.compareTo(BigDecimal.ZERO) <= 0
            && this.status.podeTransicionarPara(StatusAgendamento.AGUARDANDO_PAGAMENTO_FINAL)) {
            transicionarPara(StatusAgendamento.AGUARDANDO_PAGAMENTO_FINAL);
        }
    }

    /**
     * Registra pagamento de compra extra do e-commerce.
     * NÃO altera valorEntradaPago (que é exclusivo do pacote).
     * Apenas diminui valorRestante — o impacto no valorTotalFinal
     * já foi aplicado quando a compra foi criada (via adicionarExtras).
     */
    public void registrarPagamentoEcommerce(BigDecimal valorPago) {
        if (valorPago == null || valorPago.signum() <= 0) return;
        this.valorRestante = this.valorRestante.subtract(valorPago).max(BigDecimal.ZERO);
    }

    /**
     * Adiciona valor de extras (fotos/vídeos) ao agendamento.
     * Atualiza valorExtras e recalcula valorTotalFinal.
     *
     * Pattern: Domain Method — encapsula regra de cálculo de valor total.
     */
    public void adicionarExtras(BigDecimal valorExtras) {
        if (valorExtras == null || valorExtras.signum() <= 0) return;
        this.valorExtras = this.valorExtras.add(valorExtras);
        this.valorTotalFinal = this.valorTotal.add(this.valorExtras);
        this.valorRestante = this.valorTotalFinal.subtract(this.valorEntradaPago)
            .max(BigDecimal.ZERO);
    }
}
