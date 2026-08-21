package com.photoizer.crm.comissao.model;

/**
 * Representa a origem de uma comissão de indicação.
 *
 * - PACOTE: comissão gerada a partir da criação de agendamento com indicação
 * - INDICADOR: comissão gerada com indicador cadastrado (com ID)
 * - FOTO_EXTRA: comissão gerada pela venda de fotos extras
 * - VIDEO_EXTRA: comissão gerada pela venda de vídeos extras
 */
public enum OrigemIndicacao {
    PACOTE,
    INDICADOR,
    FOTO_EXTRA,
    VIDEO_EXTRA
}
