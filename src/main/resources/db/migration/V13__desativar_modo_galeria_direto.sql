-- Desativa o fluxo legado DIRETO da galeria.
--
-- Galerias sem uso real (selecao, compra, download ou versao editada) passam a
-- usar o fluxo COM_EDICAO desde a fase SELECAO. Galerias com uso real ficam
-- como estao para preservar as invariantes atuais e continuam apenas como
-- compatibilidade de leitura.
--
-- Migracao restrita a galerias_ensaio e configuracoes. Nenhuma linha de
-- agendamentos e criada, alterada ou removida.

update galerias_ensaio g
set modo = 'COM_EDICAO',
    fase = 'SELECAO',
    selecao_atual_id = null,
    updated_at = now()
where g.modo = 'DIRETO'
  and not exists (
      select 1 from selecoes_ensaio s
      where s.agendamento_id = g.agendamento_id
  )
  and not exists (
      select 1 from compras_extras c
      where c.agendamento_id = g.agendamento_id
  )
  and not exists (
      select 1 from fotos_ensaio f
      where f.agendamento_id = g.agendamento_id
        and (f.compra_extra_id is not null
             or f.data_download is not null
             or f.status in ('PAGA', 'AGUARDANDO_COMPROVANTE', 'AGUARDANDO_CONFIRMACAO')
             or f.versao_atual = 'EDITADA'
             or f.editada_em is not null
             or f.em_selecao_edicao = true
             or f.selecionada_pacote = true)
  );

-- Garante que novas galerias (e as que ainda nao possuem linha) usem o fluxo
-- novo, mesmo que a chave nao existisse antes.
insert into configuracoes (chave, valor)
values ('galeriaModoPadrao', 'COM_EDICAO')
on conflict (chave) do update set valor = excluded.valor;

-- Auditoria pos-migracao (esperado: nenhuma linha):
-- select agendamento_id from galerias_ensaio where modo = 'DIRETO';
