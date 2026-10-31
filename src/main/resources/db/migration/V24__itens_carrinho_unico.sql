-- Evita itens duplicados no carrinho quando duas requisições concorrentes
-- adicionam a mesma foto. Duplicatas antigas são deduplicadas mantendo o
-- registro mais antigo (menor id).

delete from itens_carrinho a
using itens_carrinho b
where a.id > b.id
  and a.session_id = b.session_id
  and a.agendamento_id = b.agendamento_id
  and a.foto_id = b.foto_id;

create unique index ux_itens_carrinho_sessao_agendamento_foto
    on itens_carrinho (session_id, agendamento_id, foto_id);
