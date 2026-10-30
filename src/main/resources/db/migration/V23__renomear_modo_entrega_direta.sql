-- Renomeia o modo `SEM_EDICAO` para `ENTREGA_DIRETA`.
--
-- O comportamento NAO muda: nao existe etapa de edicao depois da selecao; o
-- cliente recebe o arquivo que ja esta publicado na galeria. O nome antigo
-- sugeria que a galeria nao tinha fotos editadas, o que e falso (o arquivo
-- publicado pode ter sido editado antes do upload).
--
-- Migracao de mao unica: o backend novo nao le mais o valor `SEM_EDICAO`.

alter table galerias_ensaio drop constraint ck_galeria_modo;

update galerias_ensaio
   set modo = 'ENTREGA_DIRETA'
 where modo = 'SEM_EDICAO';

update configuracoes
   set valor = 'ENTREGA_DIRETA'
 where chave = 'galeriaModoPadrao'
   and valor = 'SEM_EDICAO';

alter table galerias_ensaio
    add constraint ck_galeria_modo
    check (modo in ('DIRETO', 'COM_EDICAO', 'ENTREGA_DIRETA'));
