-- Novos tipos de notificação do e-commerce: solicitação de compra de extras e
-- envio do comprovante (o staff precisa aprovar para liberar o download).
-- A constraint é recriada porque o valor é armazenado como VARCHAR com CHECK.
-- A coluna cresce para caber COMPRA_EXTRA_COMPROVANTE_ENVIADO (32 chars).

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
        'COMPRA_EXTRA_COMPROVANTE_ENVIADO'
    ));
