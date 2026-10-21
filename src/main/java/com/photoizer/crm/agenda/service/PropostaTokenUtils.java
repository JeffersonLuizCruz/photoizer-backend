package com.photoizer.crm.agenda.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.shared.util.HashUtils;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Gera o token público do link de assinatura da proposta/contrato.
 * O token em claro é exposto apenas na resposta do staff; no banco fica o hash.
 */
final class PropostaTokenUtils {

    private PropostaTokenUtils() {
    }

    static void gerar(Agendamento agendamento, int diasValidade) {
        var token = UUID.randomUUID().toString();
        var hash = HashUtils.sha256(token);
        agendamento.definirTokenProposta(token, hash, LocalDateTime.now().plusDays(diasValidade));
    }
}
