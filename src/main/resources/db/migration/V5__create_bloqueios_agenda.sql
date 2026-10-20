-- V5 - Compromissos/indisponibilidades criados pelo staff na agenda.
-- Um bloqueio pode ser global (profissional_id nulo) ou restrito a um profissional.
-- O periodo usa intervalo semiaberto [inicio, fim); "dia inteiro" e representado
-- por inicio=00:00 e fim=00:00 do dia seguinte.
create table bloqueios_agenda (
    id uuid not null,
    ativo boolean not null,
    cancelado_em timestamp(6),
    cancelado_por_id uuid,
    criado_por_id uuid not null,
    created_at timestamp(6) not null,
    created_by varchar(255) not null,
    descricao varchar(500),
    fim timestamp(6) not null,
    inicio timestamp(6) not null,
    profissional_id uuid,
    titulo varchar(120) not null,
    updated_at timestamp(6) not null,
    primary key (id),
    constraint ck_bloqueios_agenda_periodo check (fim > inicio)
);

alter table bloqueios_agenda
    add constraint fk_bloqueios_agenda_profissional
    foreign key (profissional_id) references users;

alter table bloqueios_agenda
    add constraint fk_bloqueios_agenda_criador
    foreign key (criado_por_id) references users;

alter table bloqueios_agenda
    add constraint fk_bloqueios_agenda_cancelador
    foreign key (cancelado_por_id) references users;

create index idx_bloqueios_agenda_periodo
    on bloqueios_agenda (inicio, fim)
    where ativo = true;

create index idx_bloqueios_agenda_profissional_periodo
    on bloqueios_agenda (profissional_id, inicio, fim)
    where ativo = true;
