package com.photoizer.crm.agenda.api;

import com.photoizer.crm.agenda.model.Agendamento;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    BigDecimal valorTotalFinal,
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
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static AgendamentoResponse of(Agendamento a) {
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
            a.getValorTotalFinal(),
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
            a.getCreatedAt(),
            a.getUpdatedAt()
        );
    }
}
