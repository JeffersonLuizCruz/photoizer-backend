-- Leads do checkout público de pacotes. O checkout não cobra nada: registra
-- o interesse do cliente para o comercial entrar em contato.

create table leads_pacote (
    id uuid not null,
    pacote_id uuid not null,
    pacote_nome varchar(100) not null,
    valor_base numeric(10,2),
    preco_foto_extra numeric(10,2),
    nome varchar(120) not null,
    email varchar(160),
    telefone varchar(20) not null,
    opcao_entrega varchar(20) not null,
    forma_pagamento varchar(20) not null,
    quantidade_extras integer not null default 0,
    observacao varchar(2000),
    status varchar(20) not null,
    created_at timestamp(6) not null,
    created_by varchar(255) not null,
    updated_at timestamp(6) not null,
    primary key (id)
);

create index ix_leads_pacote_status_created
    on leads_pacote (status, created_at desc);

alter table notificacoes
    alter column tipo type varchar(60);

do $$
declare
    c record;
begin
    for c in
        select conname
        from pg_constraint
        where conrelid = 'notificacoes'::regclass
          and contype = 'c'
          and pg_get_constraintdef(oid) like '%tipo%'
    loop
        execute format('alter table notificacoes drop constraint %I', c.conname);
    end loop;
end $$;

alter table notificacoes
    add constraint notificacoes_tipo_check
    check (tipo in (
        'NOVO_ENSAIO',
        'ENSAIO_REALIZADO',
        'PAGAMENTO_FINAL',
        'ENSAIO_REATRIBUIDO',
        'SELECAO_ENVIADA',
        'ENTREGA_PENDENTE',
        'COMPRA_EXTRA_SOLICITADA',
        'COMPRA_EXTRA_COMPROVANTE_ENVIADO',
        'LEAD_PACOTE'
    ));
