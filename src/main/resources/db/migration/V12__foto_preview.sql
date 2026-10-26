-- Prévia protegida para o cliente.
--
-- preview_path: versão 1024px com marca d'água gerada no upload.
-- editada_preview_path: versão 1024px da edição publicada.
--
-- Fotos antigas ficam com as colunas nulas; o endpoint público gera uma
-- versão reduzida a partir do watermark existente, preservando a marca antiga.

alter table fotos_ensaio
    add column preview_path varchar(500),
    add column editada_preview_path varchar(500);
