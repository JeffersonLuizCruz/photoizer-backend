package com.photoizer.crm.comissao.event;

import com.photoizer.crm.comissao.model.OrigemIndicacao;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Evento de domínio publicado quando um módulo externo (ex: financeiro)
 * solicita a criação de uma comissão de indicação.
 *
 * Pattern: Domain Event — Elimina escrita cross-module direta.
 * O módulo financeiro publica este evento ao vender fotos/vídeos extras,
 * e o módulo comissao consome via @EventListener para criar a Indicacao.
 * Isso mantém o princípio de que comissao é o único dono da escrita
 * na entidade Indicacao, respeitando os limites do Spring Modulith.
 */
public record ComissaoSolicitadaEvent(
    UUID agendamentoId,
    UUID indicadorId,
    String indicadorNome,
    String indicadorTelefone,
    OrigemIndicacao origem,
    BigDecimal valorReferencia
) {}
