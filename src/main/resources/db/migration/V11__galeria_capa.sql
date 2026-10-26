-- Capa manual da galeria: foto escolhida pelo staff e versão congelada.
-- A versão é congelada para que uma edição posterior não troque a capa
-- automaticamente; o staff precisa escolher novamente para atualizar.

alter table galerias_ensaio
    add column capa_foto_id uuid,
    add column capa_versao varchar(20);

alter table galerias_ensaio
    add constraint fk_galeria_capa_foto
    foreign key (capa_foto_id) references fotos_ensaio(id) on delete set null;

alter table galerias_ensaio
    add constraint ck_galeria_capa_versao
    check (capa_versao is null or capa_versao in ('ORIGINAL', 'EDITADA'));
