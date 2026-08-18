package com.photoizer.crm.contrato.api;

import com.photoizer.crm.auth.model.Papel;
import com.photoizer.crm.contrato.model.Contrato;
import com.photoizer.crm.contrato.model.StatusContrato;
import com.photoizer.crm.shared.model.TipoRepasse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ContratoResponse(
    UUID id,
    StatusContrato status,
    String token,
    UUID clienteId,
    String clienteNome,
    String clienteTelefone,
    String clienteEmail,
    String clienteCpf,
    String clienteCidade,
    String clienteEstado,
    Boolean autorizaUsoImagem,
    String urlComprovanteEntrada,
    UUID pacoteId,
    String pacoteNome,
    BigDecimal valorPacote,
    UUID editorId,
    LocalDateTime dataHoraEnsaio,
    Integer duracaoMinutos,
    String localEnsaio,
    String enderecoCompleto,
    BigDecimal taxaDeslocamento,
    BigDecimal percentualEntrada,
    BigDecimal valorTotal,
    BigDecimal valorEntradaExigido,
    BigDecimal valorRestante,
    LocalDateTime publicadoEm,
    LocalDateTime tokenExpiracao,
    LocalDateTime dataAssinatura,
    LocalDateTime dataPagamentoConfirmado,
    LocalDateTime dataAprovacao,
    LocalDateTime dataDevolucao,
    String tipoMotivoDevolucao,
    String motivoDevolucao,
    UUID agendamentoId,
    String urlPdf,
    String observacoes,
    String snapshotHash,
    UUID indicadorId,
    String indicadorNome,
    String indicadorTelefone,
    List<FotografoContrato> fotografos
) {
    public record FotografoContrato(
        UUID fotografoId,
        String fotografoNome,
        BigDecimal valorRepassar,
        TipoRepasse tipoValor,
        BigDecimal percentual,
        Papel papelParceiro
    ) {}

    public static ContratoResponse of(Contrato c) {
        var fotografos = c.getFotografos() == null ? List.<FotografoContrato>of()
            : c.getFotografos().stream()
                .map(cf -> new FotografoContrato(
                    cf.getFotografo().getId(),
                    cf.getFotografo().getNome(),
                    cf.getValorRepassar(),
                    cf.getTipoValor() != null ? cf.getTipoValor() : TipoRepasse.FIXO,
                    cf.getPercentual(),
                    cf.getPapelParceiro()
                ))
                .toList();
        return new ContratoResponse(
            c.getId(), c.getStatus(), c.getToken(), c.getClienteId(),
            c.getClienteNome(), c.getClienteTelefone(), c.getClienteEmail(),
            c.getClienteCpf(), c.getClienteCidade(), c.getClienteEstado(),
            c.getAutorizaUsoImagem(), c.getUrlComprovanteEntrada(),
            c.getPacoteId(), c.getPacoteNome(), c.getValorPacote(),
            c.getEditorId(), c.getDataHoraEnsaio(), c.getDuracaoMinutos(),
            c.getLocalEnsaio(), c.getEnderecoCompleto(),
            c.getTaxaDeslocamento(), c.getPercentualEntrada(),
            c.getValorTotal(), c.getValorEntradaExigido(), c.getValorRestante(),
            c.getPublicadoEm(), c.getTokenExpiracao(),
            c.getDataAssinatura(), c.getDataPagamentoConfirmado(),
            c.getDataAprovacao(), c.getDataDevolucao(),
            c.getTipoMotivoDevolucao(), c.getMotivoDevolucao(),
            c.getAgendamentoId(), c.getUrlPdf(), c.getObservacoes(),
            c.getSnapshotHash(),
            c.getIndicadorId(), c.getIndicadorNome(), c.getIndicadorTelefone(),
            fotografos
        );
    }
}