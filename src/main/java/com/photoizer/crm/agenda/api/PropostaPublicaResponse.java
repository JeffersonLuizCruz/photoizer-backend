package com.photoizer.crm.agenda.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(name = "PropostaPublicaResponse", description = "Proposta exibida na página pública de assinatura.")
public record PropostaPublicaResponse(
    @Schema(description = "Status atual da proposta") String status,
    @Schema(description = "Indica se ainda pode ser assinada") boolean podeAssinar,
    @Schema(description = "Nome da contratada (estúdio)") String contratadaNome,
    @Schema(description = "CNPJ da contratada") String contratadaCnpj,
    @Schema(description = "Cidade da contratada") String contratadaCidade,
    @Schema(description = "Chave PIX para pagamento") String pixChave,
    @Schema(description = "Tipo da chave PIX") String pixTipoChave,
    @Schema(description = "Nome do pacote") String pacoteNome,
    @Schema(description = "Valor do pacote") BigDecimal valorPacote,
    @Schema(description = "Preço da foto extra") BigDecimal precoFotoExtra,
    @Schema(description = "Data/hora do ensaio") LocalDateTime dataHoraEnsaio,
    @Schema(description = "Duração em minutos") Integer duracaoMinutos,
    @Schema(description = "Duração do ensaio formatada (ex.: 2h, 1h30, 45 min, Dia inteiro)") String duracaoLabel,
    @Schema(description = "Local do ensaio") String localEnsaio,
    @Schema(description = "Taxa de deslocamento") BigDecimal taxaDeslocamento,
    @Schema(description = "Percentual da entrada") BigDecimal percentualEntrada,
    @Schema(description = "Valor total") BigDecimal valorTotal,
    @Schema(description = "Valor da entrada exigida") BigDecimal valorEntradaExigido,
    @Schema(description = "Valor restante") BigDecimal valorRestante,
    @Schema(description = "Cláusulas em HTML") String clausulasHtml,
    @Schema(description = "Nome do fotógrafo responsável") String fotografoResponsavel,
    @Schema(description = "Profissionais do ensaio") List<ProfissionalEnsaio> fotografos
) {
    @Schema(name = "ProfissionalEnsaio", description = "Profissional exibido na proposta.")
    public record ProfissionalEnsaio(
        @Schema(description = "Nome") String nome,
        @Schema(description = "Papel") String papel) {}
}
