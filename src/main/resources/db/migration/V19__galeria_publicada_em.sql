-- Momento da primeira publicacao das fotos da galeria.
--
-- Usado pela Central de Galerias para medir o tempo ensaio -> publicacao e
-- calcular o prazo de entrega. Migracao aditiva: linhas existentes ficam com
-- `publicada_em` nulo ate a proxima publicacao.

alter table galerias_ensaio
    add column publicada_em timestamp;
