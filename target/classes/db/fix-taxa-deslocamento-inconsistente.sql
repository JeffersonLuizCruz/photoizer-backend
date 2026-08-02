-- =============================================================================
-- Migration: Fix inconsistent taxa_deslocamento records
-- 
-- Problema: 3 registros possuem taxa_deslocamento != custo_deslocamento
-- quando repassar_deslocamento = true, inflando valor_total e
-- valor_total_final.
--
-- Recalcula:
--   taxa_deslocamento = repassar ? custo_deslocamento : 0
--   valor_total       = 500 (pacote base = 500)
--   valor_total_final = valor_total + valor_extras
--   valor_entrada_exigido = 30% de valor_total
--   valor_restante   = max(0, valor_total - valor_entrada_pago)
-- =============================================================================

UPDATE agendamentos
SET
    taxa_deslocamento     = CASE WHEN repassar_deslocamento = TRUE THEN COALESCE(custo_deslocamento, 0) ELSE 0 END,
    valor_total           = 500 + CASE WHEN repassar_deslocamento = TRUE THEN COALESCE(custo_deslocamento, 0) ELSE 0 END,
    valor_total_final     = 500 + CASE WHEN repassar_deslocamento = TRUE THEN COALESCE(custo_deslocamento, 0) ELSE 0 END + COALESCE(valor_extras, 0),
    valor_entrada_exigido = ROUND((500 + CASE WHEN repassar_deslocamento = TRUE THEN COALESCE(custo_deslocamento, 0) ELSE 0 END) * 0.30, 2),
    valor_restante        = GREATEST(0, 500 + CASE WHEN repassar_deslocamento = TRUE THEN COALESCE(custo_deslocamento, 0) ELSE 0 END - COALESCE(valor_entrada_pago, 0))
WHERE
    repassar_deslocamento = TRUE
    AND taxa_deslocamento != COALESCE(custo_deslocamento, 0)
    AND id IN ('11aa6de2-a19b-4df2-b262-14bf3c1a882a', 'fa24b316-b534-4161-bbc4-0350ee78fa6d', '291c0f54-ef6c-4cca-b35d-f9eafb40c66a');
