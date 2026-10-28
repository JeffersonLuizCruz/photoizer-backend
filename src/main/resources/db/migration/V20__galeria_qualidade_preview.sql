-- Qualidade das prévias públicas por galeria.
--
-- O padrão ORIGINAL preserva a experiência atual do cliente. O staff pode
-- reduzir conscientemente para economizar armazenamento/banda ou proteger mais
-- o material. O arquivo de download continua sendo o original/editado.
alter table galerias_ensaio
    add column qualidade_preview varchar(20) not null default 'ORIGINAL';
