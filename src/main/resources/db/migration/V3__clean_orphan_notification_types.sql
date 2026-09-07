-- Migration: Limpar dados órfãos do enum TipoNotificacao
-- Data: 2026-09-07
-- Motivo: Valores LEMBRETE_ENSAIO, REPASSE_FOTOGRAFO e SISTEMA foram removidos
--         do enum Java (TipoNotificacao.java) mas podem existir no banco de dados.
--         O Hibernate com ddl-auto=update não remove dados, causando
--         IllegalArgumentException ao tentar desserializar esses valores.

DELETE FROM notificacoes WHERE tipo IN ('LEMBRETE_ENSAIO', 'REPASSE_FOTOGRAFO', 'SISTEMA');
