-- Modo de galeria sem edicao.
--
-- `SEM_EDICAO`: o cliente envia a selecao, o staff apenas a aprova e o pacote
-- e entregue com os arquivos originais. `DIRETO` permanece apenas como
-- compatibilidade de leitura das linhas antigas.
--
-- Migracao aditiva: nenhuma galeria existente muda de modo.

alter table galerias_ensaio drop constraint ck_galeria_modo;

alter table galerias_ensaio
    add constraint ck_galeria_modo
    check (modo in ('DIRETO', 'COM_EDICAO', 'SEM_EDICAO'));
