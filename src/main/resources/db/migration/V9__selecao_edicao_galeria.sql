-- Fase de selecao/edicao da galeria.
--
-- Adiciona a versao editada a FotoEnsaio (sem remover o RAW), o modo/fase da
-- galeria, o snapshot imutavel da selecao enviada pelo cliente e o preco
-- congelado dos extras.
--
-- Migracao aditiva. Galerias existentes sao backfilladas como DIRETO /
-- COMERCIALIZACAO para preservar exatamente o comportamento atual.

alter table fotos_ensaio
    add column versao_atual varchar(20) not null default 'ORIGINAL',
    add column editada_path varchar(500),
    add column editada_watermarked_path varchar(500),
    add column editada_thumb_path varchar(500),
    add column editada_file_name varchar(255),
    add column editada_em timestamp(6),
    add column editada_por varchar(255),
    add column em_selecao_edicao boolean not null default false;

alter table fotos_ensaio
    add constraint ck_fotos_versao_atual
    check (versao_atual in ('ORIGINAL', 'EDITADA'));

create table galerias_ensaio (
    agendamento_id uuid not null,
    modo varchar(20) not null,
    fase varchar(30) not null,
    selecao_atual_id uuid,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    created_by varchar(255) not null,
    primary key (agendamento_id),
    constraint ck_galeria_modo check (modo in ('DIRETO', 'COM_EDICAO')),
    constraint ck_galeria_fase check (fase in (
        'SELECAO', 'SELECAO_ENVIADA', 'EM_EDICAO', 'COMERCIALIZACAO'
    ))
);

create table selecoes_ensaio (
    id uuid not null,
    agendamento_id uuid not null,
    numero integer not null,
    status varchar(30) not null,
    observacao_cliente text,
    enviada_em timestamp(6) not null,
    iniciada_em timestamp(6),
    concluida_em timestamp(6),
    publicada_em timestamp(6),
    originais_baixados_em timestamp(6),
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    created_by varchar(255) not null,
    primary key (id),
    unique (agendamento_id, numero),
    constraint ck_selecao_status check (status in (
        'ENVIADA', 'EM_EDICAO', 'CONCLUIDA', 'PUBLICADA', 'CANCELADA'
    ))
);

create table selecao_ensaio_itens (
    id uuid not null,
    selecao_id uuid not null,
    foto_id uuid not null,
    numero_galeria integer not null,
    tipo varchar(10) not null,
    ordem integer not null,
    nome_arquivo varchar(255),
    valor_unitario numeric(10,2),
    editada boolean not null default false,
    editada_em timestamp(6),
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    created_by varchar(255) not null,
    primary key (id),
    unique (selecao_id, foto_id),
    constraint fk_selecao_item_selecao
        foreign key (selecao_id) references selecoes_ensaio(id),
    constraint ck_selecao_item_tipo check (tipo in ('PACOTE', 'EXTRA'))
);

-- Uma unica selecao ativa por ensaio. O indice parcial e a defesa final
-- contra duplo clique/requisicoes concorrentes.
create unique index uk_selecao_ativa
    on selecoes_ensaio (agendamento_id)
    where status in ('ENVIADA', 'EM_EDICAO', 'CONCLUIDA');

create index idx_selecoes_agendamento_status
    on selecoes_ensaio (agendamento_id, status);

create index idx_selecao_itens_foto
    on selecao_ensaio_itens (foto_id);

-- Preserva o fluxo atual das galerias existentes.
insert into galerias_ensaio (
    agendamento_id, modo, fase, created_at, updated_at, created_by
)
select id, 'DIRETO', 'COMERCIALIZACAO', now(), now(), 'MIGRATION'
from agendamentos
where not exists (
    select 1 from galerias_ensaio g where g.agendamento_id = agendamentos.id
);
