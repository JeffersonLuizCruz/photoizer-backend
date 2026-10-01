package com.photoizer.crm.agenda.service;

import com.photoizer.crm.shared.model.TipoRepasse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Dados para criação de uma proposta/pré-reserva a partir do calendário.
 * O cliente é preenchido depois, pelo próprio cliente, no link público de assinatura.
 */
public record CriarPropostaCommand(
    UUID pacoteId,
    UUID editorId,
    UUID fotografoId,
    LocalDateTime dataHoraEnsaio,
    Integer duracaoMinutos,
    String localEnsaio,
    BigDecimal custoDeslocamento,
    Boolean repassarDeslocamento,
    String clausulasPersonalizadas,
    String observacoes,
    UUID indicadorId,
    String indicadorNome,
    String indicadorTelefone,
    List<FotografoRepasse> fotografos
) {
    public record FotografoRepasse(UUID fotografoId, BigDecimal valorRepassar, TipoRepasse tipoValor, BigDecimal percentual) {
        public FotografoRepasse(UUID fotografoId, BigDecimal valorRepassar) {
            this(fotografoId, valorRepassar, TipoRepasse.FIXO, null);
        }
    }
}
