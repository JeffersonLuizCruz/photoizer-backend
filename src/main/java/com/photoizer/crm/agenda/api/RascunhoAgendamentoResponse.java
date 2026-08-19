package com.photoizer.crm.agenda.api;

import java.math.BigDecimal;
import java.util.UUID;

public record RascunhoAgendamentoResponse(
    UUID id,
    String clienteId,
    String nome,
    String telefone,
    String email,
    String cpf,
    String cidade,
    String estado,
    String origem,
    String pacoteId,
    String data,
    String hora,
    String localEnsaio,
    String enderecoCompleto,
    String editorId,
    BigDecimal custoDeslocamento,
    Boolean repassarDeslocamento,
    Boolean autorizaUsoImagem,
    String indicadorId,
    String indicadorNome,
    String indicadorTelefone,
    String observacoes,
    Integer currentStep,
    String comprovanteName,
    Boolean confirmado
) {
}
