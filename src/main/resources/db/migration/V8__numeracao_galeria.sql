-- Numeracao sequencial e estavel das fotos publicadas na galeria.
--
-- A numeracao e atribuida no momento da publicacao, na ordem de exibicao
-- (ordem), e nunca e reutilizada. Fotos INEDITA ou ocultas permanecem sem
-- numero ate serem publicadas/exibidas.
--
-- Migracao aditiva: nao altera nem remove dados existentes.

alter table fotos_ensaio
    add column numero_galeria integer;

-- Backfill das galerias existentes: somente fotos publicadas e visiveis.
with numeradas as (
    select id,
           row_number() over (
               partition by agendamento_id
               order by ordem asc, created_at asc, id asc
           ) as numero
    from fotos_ensaio
    where status in ('PUBLICADA', 'AGUARDANDO_COMPROVANTE', 'AGUARDANDO_CONFIRMACAO', 'PAGA')
      and coalesce(visivel, true) = true
)
update fotos_ensaio f
set numero_galeria = n.numero::integer
from numeradas n
where f.id = n.id;

-- Garante unicidade por galeria sem impedir varias fotos sem numero.
create unique index uk_fotos_numero_galeria
    on fotos_ensaio (agendamento_id, numero_galeria)
    where numero_galeria is not null;

comment on column fotos_ensaio.numero_galeria is
    'Numero sequencial visivel da foto na galeria (1..N), estavel e nao reutilizado.';
