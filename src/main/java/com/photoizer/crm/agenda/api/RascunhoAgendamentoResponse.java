package com.photoizer.crm.agenda.api;

import com.photoizer.crm.agenda.model.RascunhoAgendamento;

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
    public static RascunhoAgendamentoResponse of(RascunhoAgendamento r) {
        return new RascunhoAgendamentoResponse(
            r.getId(),
            r.getClienteId(),
            r.getNome(),
            r.getTelefone(),
            r.getEmail(),
            r.getCpf(),
            r.getCidade(),
            r.getEstado(),
            r.getOrigem(),
            r.getPacoteId(),
            r.getData() != null ? r.getData().toString() : null,
            r.getHora(),
            r.getLocalEnsaio(),
            r.getEnderecoCompleto(),
            r.getEditorId(),
            r.getCustoDeslocamento(),
            r.getRepassarDeslocamento(),
            r.getAutorizaUsoImagem(),
            r.getIndicadorId(),
            r.getIndicadorNome(),
            r.getIndicadorTelefone(),
            r.getObservacoes(),
            r.getCurrentStep(),
            r.getComprovanteName(),
            r.getConfirmado()
        );
    }
}
