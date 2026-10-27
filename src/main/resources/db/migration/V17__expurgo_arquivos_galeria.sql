-- Expurgo de arquivos de galerias finalizadas.
--
-- O registro da foto e o histórico comercial (numeração, seleção, compras,
-- downloads) são preservados; apenas os arquivos em disco são removidos.
-- A coluna bytes_liberados guarda o total apagado por foto para relatórios.

ALTER TABLE fotos_ensaio
    ADD COLUMN arquivos_removidos boolean NOT NULL DEFAULT false,
    ADD COLUMN arquivos_removidos_em timestamp(6),
    ADD COLUMN arquivos_removidos_por varchar(255),
    ADD COLUMN bytes_liberados bigint;

CREATE INDEX idx_fotos_ensaio_arquivadas
    ON fotos_ensaio (agendamento_id)
    WHERE arquivos_removidos;
