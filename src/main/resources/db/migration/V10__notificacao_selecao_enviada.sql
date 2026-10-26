-- Novo tipo de notificação: seleção enviada pelo cliente para edição.
-- A constraint é recriada porque o valor é armazenado como VARCHAR com CHECK.

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
        'SELECAO_ENVIADA'
    ));
