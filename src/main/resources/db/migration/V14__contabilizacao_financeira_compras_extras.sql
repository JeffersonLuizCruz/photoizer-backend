-- V14 - Contabilização financeira das compras extras do e-commerce.
--
-- As flags permitem que o reconciliador de boot diferencie uma compra que já
-- entrou em valorExtras/valorTotalFinal (extras_contabilizados) de uma compra
-- cujo pagamento já foi refletido em valorEntradaPago (pagamento_contabilizado).
--
-- Compras antigas entram como FALSE e são reparadas pelo
-- ReconciliarComprasExtraFinanceiro. Caso algum ambiente já tenha contabilizado
-- manualmente uma compra anterior a esta versão, marque as flags como TRUE
-- antes de subir para evitar duplicidade.

ALTER TABLE compras_extras
    ADD COLUMN extras_contabilizados BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE compras_extras
    ADD COLUMN pagamento_contabilizado BOOLEAN NOT NULL DEFAULT FALSE;
