package com.photoizer.crm.agenda.api;

import com.photoizer.crm.agenda.model.RepasseStatus;
import com.photoizer.crm.shared.model.FormaPagamento;
import com.photoizer.crm.shared.model.TipoRepasse;
import com.photoizer.crm.auth.model.Papel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(name = "AgendamentoResponse",
    description = "Agendamento/ensaio. Métricas internas (custos, partilha, lucro e repasses) "
        + "são omitidas para o papel AGENDADOR.")
public record AgendamentoResponse(
    @Schema(description = "ID do agendamento") UUID id,
    @Schema(description = "ID do cliente") UUID clienteId,
    @Schema(description = "Nome do cliente") String clienteNome,
    @Schema(description = "Telefone do cliente") String clienteTelefone,
    @Schema(description = "E-mail do cliente") String clienteEmail,
    @Schema(description = "CPF do cliente") String clienteCpf,
    @Schema(description = "Cidade do cliente") String clienteCidade,
    @Schema(description = "UF do cliente") String clienteEstado,
    @Schema(description = "ID do pacote") UUID pacoteId,
    @Schema(description = "Nome do pacote") String pacoteNome,
    @Schema(description = "Cor identificadora do pacote (#RRGGBB)") String pacoteCor,
    @Schema(description = "ID do editor") UUID editorId,
    @Schema(description = "Nome do editor") String editorNome,
    @Schema(description = "ID do fotógrafo responsável") UUID fotografoId,
    @Schema(description = "Nome do fotógrafo responsável") String fotografoNome,
    @Schema(description = "Data/hora do ensaio") LocalDateTime dataHoraEnsaio,
    @Schema(description = "Duração em minutos") Integer duracaoMinutos,
    @Schema(description = "Local do ensaio") String localEnsaio,
    @Schema(description = "Valor total do pacote") BigDecimal valorTotal,
    @Schema(description = "Valor da entrada exigida") BigDecimal valorEntradaExigido,
    @Schema(description = "Valor da entrada já pago") BigDecimal valorEntradaPago,
    @Schema(description = "Valor restante a pagar") BigDecimal valorRestante,
    @Schema(description = "Valor de extras") BigDecimal valorExtras,
    @Schema(description = "Taxa de deslocamento cobrada") BigDecimal taxaDeslocamento,
    @Schema(description = "Custo de deslocamento (métrica interna)") BigDecimal custoDeslocamento,
    @Schema(description = "Indica se o deslocamento é repassado") Boolean repassarDeslocamento,
    @Schema(description = "Valor total final (pacote + extras + deslocamento)") BigDecimal valorTotalFinal,
    @Schema(description = "Percentual da entrada") BigDecimal percentualEntrada,
    @Schema(description = "Valor do pacote") BigDecimal valorPacote,
    @Schema(description = "Saldo devedor atual") BigDecimal saldoDevedor,
    @Schema(description = "Status do agendamento", example = "CONFIRMADO") String status,
    @Schema(description = "Data de confirmação") LocalDateTime dataConfirmacao,
    @Schema(description = "Data de realização") LocalDateTime dataRealizacao,
    @Schema(description = "Data do pagamento final") LocalDateTime dataPagamentoFinal,
    @Schema(description = "Data de finalização") LocalDateTime dataFinalizacao,
    @Schema(description = "Possui comprovante de entrada") boolean temComprovanteEntrada,
    @Schema(description = "Possui comprovante do pagamento final") boolean temComprovanteFinal,
    @Schema(description = "Forma de pagamento final") FormaPagamento formaPagamentoFinal,
    @Schema(description = "Autorização de uso de imagem") Boolean autorizaUsoImagem,
    @Schema(description = "Cláusulas personalizadas do contrato") String clausulasPersonalizadas,
    @Schema(description = "Token do link público da proposta") String tokenProposta,
    @Schema(description = "Data de envio da proposta ao cliente") LocalDateTime dataEnvioProposta,
    @Schema(description = "Data de assinatura da proposta") LocalDateTime dataAssinatura,
    @Schema(description = "Possui termo assinado") boolean temTermoAssinado,
    @Schema(description = "Ensaio em destaque") Boolean ensaioDestaque,
    @Schema(description = "Motivo da recusa (quando cancelado)") String motivoRecusa,
    @Schema(description = "Data da recusa") LocalDateTime dataRecusa,
    @Schema(description = "Quem recusou") String recusadoPor,
    @Schema(description = "Observações internas") String observacoes,
    @Schema(description = "Token da galeria do cliente") UUID tokenGaleria,
    @Schema(description = "Data de criação") LocalDateTime createdAt,
    @Schema(description = "Data de atualização") LocalDateTime updatedAt,
    @Schema(description = "Parceiros vinculados ao ensaio") List<FotografoNoAgendamento> fotografos,
    @Schema(description = "Valor de partilha global (métrica interna)") BigDecimal valorPartilhaGlobal,
    @Schema(description = "Lucro do CRM (métrica interna)") BigDecimal valorLucroCrm,
    @Schema(description = "Valor de comissão (métrica interna)") BigDecimal valorComissao,
    @Schema(description = "Nome do indicador") String indicadorNome,
    @Schema(description = "Status da comissão") String statusComissao
) {
    @Schema(name = "FotografoNoAgendamento", description = "Profissional vinculado a um ensaio.")
    public record FotografoNoAgendamento(
        @Schema(description = "ID do profissional") UUID fotografoId,
        @Schema(description = "Nome do profissional") String fotografoNome,
        @Schema(description = "Valor a repassar (métrica interna)") BigDecimal valorRepassar,
        @Schema(description = "Status do repasse") RepasseStatus status,
        @Schema(description = "Data de pagamento do repasse") LocalDateTime dataPagamento,
        @Schema(description = "Tipo do repasse (FIXO/PERCENTUAL)") TipoRepasse tipoValor,
        @Schema(description = "Percentual do repasse") BigDecimal percentual,
        @Schema(description = "Papel do parceiro") Papel papelParceiro
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
            clienteCidade, clienteEstado, pacoteId, pacoteNome, pacoteCor, editorId, editorNome,
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
