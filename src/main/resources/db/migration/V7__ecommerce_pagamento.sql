-- Pagamento do e-commerce: PIX global (gerido dentro do modulo ecommerce) e
-- link de pagamento por cartao de credito inserido manualmente pelo staff em
-- cada compra de fotos extras.
--
-- Migracao aditiva: nao altera nem remove dados existentes.

create table ecommerce_configuracao_pagamento (
    id smallint not null,
    pix_chave varchar(200),
    pix_tipo_chave varchar(20),
    updated_at timestamp(6) not null,
    updated_by varchar(255),
    primary key (id),
    constraint ecommerce_configuracao_pagamento_singleton_check check (id = 1)
);

-- Inicializa o PIX do e-commerce com a chave ja cadastrada em Configuracoes,
-- evitando que o checkout fique sem PIX apos o deploy. O staff pode alterar
-- ou limpar esse valor independentemente a partir do modulo ecommerce.
insert into ecommerce_configuracao_pagamento (id, pix_chave, pix_tipo_chave, updated_at)
select 1,
       c.valor,
       (select t.valor from configuracoes t where t.chave = 'pixTipoChave'),
       now()
from configuracoes c
where c.chave = 'pixChave'
  and c.valor is not null
  and btrim(c.valor) <> ''
on conflict (id) do nothing;

alter table compras_extras
    add column link_pagamento_cartao varchar(1000);

alter table compras_extras
    drop constraint if exists compras_extras_metodo_pagamento_check;

alter table compras_extras
    add constraint compras_extras_metodo_pagamento_check
        check (metodo_pagamento in ('PIX', 'TRANSFERENCIA', 'DINHEIRO', 'CARTAO'));
