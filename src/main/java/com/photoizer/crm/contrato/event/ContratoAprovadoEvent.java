package com.photoizer.crm.contrato.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ContratoAprovadoEvent(
    UUID contratoId,
    UUID clienteId,
    String nome,
    String telefone,
    String email,
    String cpf,
    String cidade,
    String estado,
    UUID pacoteId,
    UUID editorId,
    LocalDateTime dataHoraEnsaio,
    Integer duracaoMinutos,
    String localEnsaio,
    String enderecoCompleto,
    BigDecimal valorTotal,
    BigDecimal valorEntradaExigido,
    BigDecimal percentualEntrada,
    String urlComprovanteEntrada,
    Boolean autorizaUsoImagem,
    String observacoes,
    UUID indicadorId,
    String indicadorNome,
    String indicadorTelefone,
    BigDecimal valorBasePacote,
    BigDecimal custoDeslocamento,
    Boolean repassarDeslocamento
) {
}