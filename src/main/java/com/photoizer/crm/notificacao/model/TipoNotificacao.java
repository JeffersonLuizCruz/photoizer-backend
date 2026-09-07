package com.photoizer.crm.notificacao.model;

/**
 * Enum dos tipos de notificação do sistema.
 *
 * Valores ativos: apenas os utilizados pelos listeners de eventos de agenda.
 * Valores mortos (LEMBRETE_ENSAIO, REPASSE_FOTOGRAFO, SISTEMA) foram removidos
 * conforme MODULE.md §7.6 — fluxos não implementados.
 */
public enum TipoNotificacao {
    NOVO_ENSAIO,
    ENSAIO_REALIZADO,
    PAGAMENTO_FINAL
}