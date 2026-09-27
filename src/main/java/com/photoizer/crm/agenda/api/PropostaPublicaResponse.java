package com.photoizer.crm.agenda.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PropostaPublicaResponse(
    String status,
    boolean podeAssinar,
    String contratadaNome,
    String contratadaCnpj,
    String contratadaCidade,
    String pixChave,
    String pixTipoChave,
    String pacoteNome,
    BigDecimal valorPacote,
    BigDecimal precoFotoExtra,
    LocalDateTime dataHoraEnsaio,
    Integer duracaoMinutos,
    String localEnsaio,
    String enderecoCompleto,
    BigDecimal taxaDeslocamento,
    BigDecimal percentualEntrada,
    BigDecimal valorTotal,
    BigDecimal valorEntradaExigido,
    BigDecimal valorRestante,
    String clausulasHtml,
    List<ProfissionalEnsaio> fotografos
) {
    public record ProfissionalEnsaio(String nome, String papel) {}
}
