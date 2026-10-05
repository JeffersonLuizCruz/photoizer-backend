package com.photoizer.crm.agenda.api;

import com.photoizer.crm.agenda.model.RepasseStatus;
import com.photoizer.crm.shared.model.FormaPagamento;
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
    UUID fotografoId,
    String fotografoNome,
    LocalDateTime dataHoraEnsaio,
    Integer duracaoMinutos,
    String localEnsaio,
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
    LocalDateTime dataPagamentoFinal,
    LocalDateTime dataFinalizacao,
    boolean temComprovanteEntrada,
    boolean temComprovanteFinal,
    FormaPagamento formaPagamentoFinal,
    Boolean autorizaUsoImagem,
    String clausulasPersonalizadas,
    String tokenProposta,
    LocalDateTime dataEnvioProposta,
    LocalDateTime dataAssinatura,
    boolean temTermoAssinado,
    Boolean ensaioDestaque,
    String motivoRecusa,
    LocalDateTime dataRecusa,
    String recusadoPor,
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

    /**
     * Cópia da resposta sem as métricas internas do estúdio (custos, comissões,
     * partilha, lucro e repasses). Usada para papéis sem visibilidade financeira
     * (ex.: AGENDADOR). Os valores cobrados do cliente (total, entrada, saldo,
     * pacote) são preservados.
     */
    public AgendamentoResponse ocultarDadosInternos() {
        var fotografosSemRepasse = fotografos == null ? null : fotografos.stream()
            .map(f -> new FotografoNoAgendamento(
                f.fotografoId(), f.fotografoNome(), null, f.status(),
                f.dataPagamento(), null, null, f.papelParceiro()))
            .toList();

        return new AgendamentoResponse(
            id, clienteId, clienteNome, clienteTelefone, clienteEmail, clienteCpf,
            clienteCidade, clienteEstado, pacoteId, pacoteNome, editorId, editorNome,
            fotografoId, fotografoNome, dataHoraEnsaio, duracaoMinutos, localEnsaio,
            valorTotal, valorEntradaExigido, valorEntradaPago, valorRestante,
            valorExtras, taxaDeslocamento, null, repassarDeslocamento,
            valorTotalFinal, percentualEntrada, valorPacote, saldoDevedor, status,
            dataConfirmacao, dataRealizacao, dataPagamentoFinal, dataFinalizacao,
            temComprovanteEntrada, temComprovanteFinal, formaPagamentoFinal,
            autorizaUsoImagem, clausulasPersonalizadas, tokenProposta, dataEnvioProposta,
            dataAssinatura, temTermoAssinado, ensaioDestaque, motivoRecusa, dataRecusa,
            recusadoPor, observacoes, tokenGaleria, createdAt, updatedAt,
            fotografosSemRepasse, null, null, null, null, null);
    }
}
