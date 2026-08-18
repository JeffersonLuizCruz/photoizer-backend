package com.photoizer.crm.agenda.api;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.RepasseStatus;
import com.photoizer.crm.shared.model.TipoRepasse;
import com.photoizer.crm.auth.model.Papel;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record AgendamentoResponse(
    UUID id,
    UUID clienteId,
    String clienteNome,
    String clienteTelefone,
    String clienteEmail,
    String clienteCpf,
    String clienteCidade,
    String clienteEstado,
    UUID pacoteId,
    String pacoteNome,
    UUID editorId,
    String editorNome,
    LocalDateTime dataHoraEnsaio,
    Integer duracaoMinutos,
    String localEnsaio,
    String enderecoCompleto,
    BigDecimal valorTotal,
    BigDecimal valorEntradaExigido,
    BigDecimal valorEntradaPago,
    BigDecimal valorRestante,
    BigDecimal valorExtras,
    BigDecimal taxaDeslocamento,
    BigDecimal custoDeslocamento,
    Boolean repassarDeslocamento,
    BigDecimal valorTotalFinal,
    BigDecimal percentualEntrada,
    BigDecimal valorPacote,
    BigDecimal saldoDevedor,
    String status,
    LocalDateTime dataConfirmacao,
    LocalDateTime dataRealizacao,
    LocalDateTime dataEnvioSelecao,
    LocalDateTime dataEntregaFinal,
    LocalDateTime dataFinalizacao,
    String urlComprovanteEntrada,
    String urlComprovanteFinal,
    Boolean autorizaUsoImagem,
    String clausulasPersonalizadas,
    Boolean contratoGerado,
    Boolean ensaioDestaque,
    String observacoes,
    UUID tokenGaleria,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<FotografoNoAgendamento> fotografos,
    BigDecimal valorPartilhaGlobal,
    BigDecimal valorLucroCrm,
    BigDecimal valorComissao,
    String indicadorNome,
    String statusComissao
) {
    public record FotografoNoAgendamento(
        UUID fotografoId,
        String fotografoNome,
        BigDecimal valorRepassar,
        RepasseStatus status,
        LocalDateTime dataPagamento,
        TipoRepasse tipoValor,
        BigDecimal percentual,
        Papel papelParceiro
    ) {}

    public static AgendamentoResponse of(Agendamento a) {
        return of(a, null, null, null);
    }

    public static AgendamentoResponse of(Agendamento a, BigDecimal valorComissao, String indicadorNome, String statusComissao) {
        var valorPacote = a.getValorTotal().subtract(a.getTaxaDeslocamento());
        var saldoDevedor = a.getValorTotalFinal().subtract(a.getValorEntradaPago());
        return new AgendamentoResponse(
            a.getId(),
            a.getCliente().getId(),
            a.getCliente().getNome(),
            a.getCliente().getTelefone(),
            a.getCliente().getEmail(),
            a.getCliente().getCpf(),
            a.getCliente().getCidade(),
            a.getCliente().getEstado(),
            a.getPacote().getId(),
            a.getPacote().getNome(),
            a.getEditor() != null ? a.getEditor().getId() : null,
            a.getEditor() != null ? a.getEditor().getNome() : null,
            a.getDataHoraEnsaio(),
            a.getDuracaoMinutos(),
            a.getLocalEnsaio(),
            a.getEnderecoCompleto(),
            a.getValorTotal(),
            a.getValorEntradaExigido(),
            a.getValorEntradaPago(),
            a.getValorRestante(),
            a.getValorExtras(),
            a.getTaxaDeslocamento(),
            a.getCustoDeslocamento() != null ? a.getCustoDeslocamento() : BigDecimal.ZERO,
            a.getRepassarDeslocamento() != null ? a.getRepassarDeslocamento() : true,
            a.getValorTotalFinal(),
            a.getPercentualEntrada() != null ? a.getPercentualEntrada() : BigDecimal.valueOf(30),
            valorPacote,
            saldoDevedor,
            a.getStatus().name(),
            a.getDataConfirmacao(),
            a.getDataRealizacao(),
            a.getDataEnvioSelecao(),
            a.getDataEntregaFinal(),
            a.getDataFinalizacao(),
            a.getUrlComprovanteEntrada(),
            a.getUrlComprovanteFinal(),
            a.getAutorizaUsoImagem(),
            a.getClausulasPersonalizadas(),
            a.getContratoGerado(),
            a.getEnsaioDestaque(),
            a.getObservacoes(),
            a.getTokenGaleria(),
            a.getCreatedAt(),
            a.getUpdatedAt(),
            null,
            a.getValorPartilhaGlobal(),
            a.getValorLucroCrm(),
            valorComissao,
            indicadorNome,
            statusComissao
        );
    }

    public static AgendamentoResponse of(Agendamento a, List<com.photoizer.crm.agenda.model.AgendamentoFotografo> links,
                                          BigDecimal valorComissao, String indicadorNome, String statusComissao) {
        var valorPacote = a.getValorTotal().subtract(a.getTaxaDeslocamento());
        var saldoDevedor = a.getValorTotalFinal().subtract(a.getValorEntradaPago());
        var fotos = links != null ? links.stream()
            .map(af -> new FotografoNoAgendamento(
                af.getFotografo().getId(),
                af.getFotografo().getNome(),
                af.getValorRepassar(),
                af.getStatus(),
                af.getDataPagamento(),
                af.getTipoValor() != null ? af.getTipoValor() : TipoRepasse.FIXO,
                af.getPercentual(),
                af.getPapelParceiro()
            ))
            .toList() : null;
        return new AgendamentoResponse(
            a.getId(),
            a.getCliente().getId(),
            a.getCliente().getNome(),
            a.getCliente().getTelefone(),
            a.getCliente().getEmail(),
            a.getCliente().getCpf(),
            a.getCliente().getCidade(),
            a.getCliente().getEstado(),
            a.getPacote().getId(),
            a.getPacote().getNome(),
            a.getEditor() != null ? a.getEditor().getId() : null,
            a.getEditor() != null ? a.getEditor().getNome() : null,
            a.getDataHoraEnsaio(),
            a.getDuracaoMinutos(),
            a.getLocalEnsaio(),
            a.getEnderecoCompleto(),
            a.getValorTotal(),
            a.getValorEntradaExigido(),
            a.getValorEntradaPago(),
            a.getValorRestante(),
            a.getValorExtras(),
            a.getTaxaDeslocamento(),
            a.getCustoDeslocamento() != null ? a.getCustoDeslocamento() : BigDecimal.ZERO,
            a.getRepassarDeslocamento() != null ? a.getRepassarDeslocamento() : true,
            a.getValorTotalFinal(),
            a.getPercentualEntrada() != null ? a.getPercentualEntrada() : BigDecimal.valueOf(30),
            valorPacote,
            saldoDevedor,
            a.getStatus().name(),
            a.getDataConfirmacao(),
            a.getDataRealizacao(),
            a.getDataEnvioSelecao(),
            a.getDataEntregaFinal(),
            a.getDataFinalizacao(),
            a.getUrlComprovanteEntrada(),
            a.getUrlComprovanteFinal(),
            a.getAutorizaUsoImagem(),
            a.getClausulasPersonalizadas(),
            a.getContratoGerado(),
            a.getEnsaioDestaque(),
            a.getObservacoes(),
            a.getTokenGaleria(),
            a.getCreatedAt(),
            a.getUpdatedAt(),
            fotos,
            a.getValorPartilhaGlobal(),
            a.getValorLucroCrm(),
            valorComissao,
            indicadorNome,
            statusComissao
        );
    }
}
