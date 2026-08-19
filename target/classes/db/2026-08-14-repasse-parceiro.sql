-- =====================================================================
-- Photoizer CRM - Migração: Repasse para múltiplos parceiros
-- Data: 2026-08-14
--
-- Objetivo: adicionar suporte a repasse FIXO (R$) ou PERCENTUAL (%)
-- e snapshot do papel do parceiro na tabela agendamento_fotografos.
--
-- Como aplicar (H2 - rodar UMA VEZ antes de subir a nova versão):
--   Em um console H2 conectado ao banco ./data/crmdb ou via script:
--   @run 2026-08-14-repasse-parceiro.sql
--
-- NOTA: as colunas são adicionadas como anuláveis e preenchidas em
-- seguida, para não falhar em tabelas com registros existentes
-- (ddl-auto=update não consegue adicionar coluna NOT NULL com dados).
-- =====================================================================

ALTER TABLE agendamento_fotografos ADD COLUMN IF NOT EXISTS tipo_valor VARCHAR(20);
ALTER TABLE agendamento_fotografos ADD COLUMN IF NOT EXISTS percentual DECIMAL(5,2);
ALTER TABLE agendamento_fotografos ADD COLUMN IF NOT EXISTS papel_parceiro VARCHAR(20);

-- Defaults retroativos para registros existentes
UPDATE agendamento_fotografos SET tipo_valor = 'FIXO' WHERE tipo_valor IS NULL;

-- RepasseStatus ganhou CANCELADO; colunas criadas pelo ddl-auto/via mapeamento
-- @Enumerated(STRING) podem ter virado ENUM restrito no H2. Normaliza para
-- VARCHAR(20), compatível com o mapeamento atual da entidade.
ALTER TABLE agendamento_fotografos ALTER COLUMN status VARCHAR(20);

UPDATE agendamento_fotografos af
SET papel_parceiro = (SELECT u.papel FROM users u WHERE u.id = af.fotografo_id)
WHERE papel_parceiro IS NULL;

-- Caso algum parceiro antigo não tenha usuário correspondente, usa FOTOGRAFO como fallback
UPDATE agendamento_fotografos SET papel_parceiro = 'FOTOGRAFO'
WHERE papel_parceiro IS NULL;

-- Recomendado: índices para consultas por parceiro
CREATE INDEX IF NOT EXISTS idx_agendamento_fotografos_fotografo ON agendamento_fotografos(fotografo_id);
CREATE INDEX IF NOT EXISTS idx_agendamento_fotografos_status ON agendamento_fotografos(status);
