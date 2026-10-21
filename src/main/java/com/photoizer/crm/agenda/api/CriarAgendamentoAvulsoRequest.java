package com.photoizer.crm.agenda.api;

import com.photoizer.crm.shared.model.FormaPagamento;
import com.photoizer.crm.shared.model.TipoPagamento;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(name = "CriarAgendamentoAvulsoRequest",
    description = "Cria um ensaio já confirmado, sem passar pelo fluxo público de proposta.")
public record CriarAgendamentoAvulsoRequest(
    @Schema(description = "Nome do cliente") @NotBlank @Size(max = 255) String clienteNome,
    @Schema(description = "Telefone do cliente") @NotBlank @Size(max = 20) String clienteTelefone,
    @Schema(description = "E-mail do cliente", nullable = true) @Email @Size(max = 255) String clienteEmail,
    @Schema(description = "CPF do cliente", nullable = true) @Size(max = 14) String clienteCpf,
    @Schema(description = "Cidade", nullable = true) @Size(max = 100) String clienteCidade,
    @Schema(description = "UF", nullable = true) @Size(max = 2) String clienteEstado,
    @Schema(description = "ID do pacote") @NotNull UUID pacoteId,
    @Schema(description = "ID do fotógrafo responsável") @NotNull UUID fotografoId,
    @Schema(description = "Data/hora do ensaio") @NotNull @Future LocalDateTime dataHoraEnsaio,
    @Schema(description = "Duração em minutos", nullable = true) Integer duracaoMinutos,
    @Schema(description = "Local do ensaio") @NotBlank @Size(max = 255) String localEnsaio,
    @Schema(description = "Valor total combinado; se vazio usa o pacote", nullable = true)
    @PositiveOrZero BigDecimal valorTotal,
    @Schema(description = "Valor da entrada exigida; se vazio usa o percentual configurado", nullable = true)
    @PositiveOrZero BigDecimal valorEntradaExigido,
    @Schema(description = "Valor recebido agora", nullable = true) @PositiveOrZero BigDecimal valorPago,
    @Schema(description = "Data do recebimento", nullable = true) LocalDateTime dataPagamento,
    @Schema(description = "Forma do recebimento", nullable = true) FormaPagamento formaPagamento,
    @Schema(description = "Tipo do recebimento", nullable = true) TipoPagamento tipoPagamento,
    @Schema(description = "Custo de deslocamento", nullable = true) @PositiveOrZero BigDecimal custoDeslocamento,
    @Schema(description = "Repassar deslocamento", nullable = true) Boolean repassarDeslocamento,
    @Schema(description = "Autorização de uso de imagem", nullable = true) Boolean autorizaUsoImagem,
    @Schema(description = "Observações internas", nullable = true) String observacoes
) {}
