-- V3 - Tabelas do Event Publication Registry do Spring Modulith.
--
-- A entidade DefaultJpaEventPublication fica no pacote do Spring Modulith
-- (org.springframework.modulith.events.jpa), fora de com.photoizer.crm, e por
-- isso nao foi capturada pelo V1__baseline.sql (gerado apenas a partir das
-- entidades do CRM). Sem estas tabelas o Hibernate falha no boot com
-- "Schema validation: missing table [event_publication]".
--
-- DDL gerado com o mesmo naming strategy do baseline
-- (mvn -Dtest=SchemaBaselineGenerator test; o gerador agora inclui as entidades
-- do Modulith).

create table if not exists event_publication (
    id uuid not null,
    completion_attempts integer not null,
    completion_date timestamp(6) with time zone,
    event_type varchar(255) not null,
    last_resubmission_date timestamp(6) with time zone,
    listener_id varchar(255) not null,
    publication_date timestamp(6) with time zone not null,
    serialized_event varchar(255) not null,
    status varchar(255) check ((status in ('PUBLISHED','PROCESSING','COMPLETED','FAILED','RESUBMITTED'))),
    primary key (id)
);

create index if not exists event_publication_by_completion_date_idx
    on event_publication (completion_date);

create table if not exists event_publication_archive (
    id uuid not null,
    completion_attempts integer not null,
    completion_date timestamp(6) with time zone,
    event_type varchar(255) not null,
    last_resubmission_date timestamp(6) with time zone,
    listener_id varchar(255) not null,
    publication_date timestamp(6) with time zone not null,
    serialized_event varchar(255) not null,
    status varchar(255) check ((status in ('PUBLISHED','PROCESSING','COMPLETED','FAILED','RESUBMITTED'))),
    primary key (id)
);

create index if not exists event_publication_archive_by_completion_date_idx
    on event_publication_archive (completion_date);
