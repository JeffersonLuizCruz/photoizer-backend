-- Migration: Unificar FotoExtra + VideoExtra -> ExtraServico
-- Executar ANTES de remover as entidades FotoExtra e VideoExtra
-- Requer: Spring Boot com ddl-auto=update (Hibernate cria a tabela extras_servico automaticamente)

-- 1. Migrar fotos extras
INSERT INTO extras_servico (id, agendamento_id, tipo, quantidade, valor_unitario, valor_total,
    audit_info_created_at, audit_info_updated_at, audit_info_created_by)
SELECT
    fe.id,
    fe.agendamento_id,
    'FOTO',
    fe.quantidade,
    fe.valor_unitario,
    fe.valor_total,
    fe.audit_info_created_at,
    fe.audit_info_updated_at,
    fe.audit_info_created_by
FROM fotos_extras fe;

-- 2. Migrar vídeos extras
INSERT INTO extras_servico (id, agendamento_id, tipo, quantidade, valor_unitario, valor_total,
    audit_info_created_at, audit_info_updated_at, audit_info_created_by)
SELECT
    ve.id,
    ve.agendamento_id,
    'VIDEO',
    ve.quantidade,
    ve.valor_unitario,
    ve.valor_total,
    ve.audit_info_created_at,
    ve.audit_info_updated_at,
    ve.audit_info_created_by
FROM videos_extras ve;

-- 3. Após validação, remover tabelas antigas (executar manualmente):
-- DROP TABLE IF EXISTS fotos_extras;
-- DROP TABLE IF EXISTS videos_extras;
