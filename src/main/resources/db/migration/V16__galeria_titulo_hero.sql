-- Texto customizado do hero da galeria.
-- Nulo/vazio mantem o comportamento atual: o nome do cliente e exibido.

alter table galerias_ensaio
    add column titulo_hero varchar(120);
