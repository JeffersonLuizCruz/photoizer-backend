-- Agendamento avulso: criação direta de ensaios já confirmados pelo staff.
-- Migração aditiva: não altera nem remove dados existentes.

alter table agendamentos
    add column origem varchar(20) not null default 'PROPOSTA';

alter table agendamentos
    add constraint agendamentos_origem_check
        check (origem in ('PROPOSTA', 'AVULSO', 'IMPORTADO'));

alter table agendamentos
    add column valores_personalizados boolean not null default false;

alter table agendamentos
    add column referencia_externa varchar(100);

alter table pagamentos
    add column forma_pagamento varchar(20);

alter table pagamentos
    add constraint pagamentos_forma_pagamento_check
        check (forma_pagamento in ('PIX', 'CARTAO', 'DINHEIRO', 'TRANSFERENCIA', 'OUTRO'));

alter table pagamentos
    add column tipo_pagamento varchar(20);

alter table pagamentos
    add constraint pagamentos_tipo_pagamento_check
        check (tipo_pagamento in ('ENTRADA', 'FINAL', 'EXTRA'));

create index idx_agendamentos_origem on agendamentos (origem);
