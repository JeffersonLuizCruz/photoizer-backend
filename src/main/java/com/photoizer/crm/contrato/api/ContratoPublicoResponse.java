package com.photoizer.crm.contrato.api;

import com.photoizer.crm.contrato.model.Contrato;
import com.photoizer.crm.contrato.model.StatusContrato;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ContratoPublicoResponse(
    StatusContrato status,
    boolean podeAssinar,
    String motivoDevolucao,
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

    public static ContratoPublicoResponse of(
        Contrato c,
        String contratadaNome,
        String contratadaCnpj,
        String contratadaCidade,
        String pixChave,
        String pixTipoChave,
        String clausulasHtml,
        List<ProfissionalEnsaio> fotografos
    ) {
        boolean podeAssinar = c.getStatus() == StatusContrato.PUBLICADO
            || c.getStatus() == StatusContrato.DEVOLVIDO;
        return new ContratoPublicoResponse(
            c.getStatus(), podeAssinar,
            c.getMotivoDevolucao(),
            contratadaNome, contratadaCnpj, contratadaCidade,
            pixChave, pixTipoChave,
            c.getPacoteNome(), c.getValorPacote(), c.getPrecoFotoExtra(),
            c.getDataHoraEnsaio(), c.getDuracaoMinutos(),
            c.getLocalEnsaio(), c.getEnderecoCompleto(),
            c.getTaxaDeslocamento(), c.getPercentualEntrada(),
            c.getValorTotal(), c.getValorEntradaExigido(), c.getValorRestante(),
            clausulasHtml,
            fotografos
        );
    }
}