package com.photoizer.crm.agenda.api;

import com.photoizer.crm.shared.model.FormaPagamento;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(name = "CriarAgendamentoAvulsoRequest",
    description = "Cria um ensaio já confirmado. O cliente preenche os próprios dados e assina o "
        + "contrato pelo link público gerado no token da proposta.")
public record CriarAgendamentoAvulsoRequest(
    @Schema(description = "ID do pacote") @NotNull UUID pacoteId,
    @Schema(description = "ID do fotógrafo responsável") @NotNull UUID fotografoId,
    @Schema(description = "Data/hora do ensaio. Aceita datas passadas para registrar um ensaio já realizado.")
    @NotNull LocalDateTime dataHoraEnsaio,
    @Schema(description = "Duração em minutos", nullable = true) Integer duracaoMinutos,
    @Schema(description = "Local do ensaio") @NotBlank @Size(max = 255) String localEnsaio,
    @Schema(description = "Percentual de entrada sobre o total (pacote + deslocamento); "
        + "use este campo OU valorJaRecebido", nullable = true)
    @Positive BigDecimal percentualEntrada,
    @Schema(description = "Valor da reserva já recebido; use este campo OU percentualEntrada", nullable = true)
    @PositiveOrZero BigDecimal valorJaRecebido,
    @Schema(description = "Data do recebimento (usada quando valorJaRecebido é informado)", nullable = true)
    LocalDateTime dataRecebimento,
    @Schema(description = "Forma do recebimento (usada quando valorJaRecebido é informado)", nullable = true)
    FormaPagamento formaPagamento,
    @Schema(description = "Custo de deslocamento", nullable = true) @PositiveOrZero BigDecimal custoDeslocamento,
    @Schema(description = "Repassar deslocamento ao cliente", nullable = true) Boolean repassarDeslocamento,
    @Schema(description = "Observações internas", nullable = true) String observacoes
) {}
