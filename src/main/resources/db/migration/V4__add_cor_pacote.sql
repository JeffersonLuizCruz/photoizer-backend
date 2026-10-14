-- V4 - Cor identificadora do pacote, escolhida pelo usuario na paleta curada.
-- A cor e refletida na agenda de agendamentos (tarja por pacote) e nos detalhes.
--
-- Mudanca puramente aditiva: pacotes existentes recebem a cor neutra padrao
-- (cinza #64748B) e podem ser reeditados depois. Em PostgreSQL 11+ o DEFAULT
-- e aplicado como metadado (fast default), sem reescrever a tabela.
ALTER TABLE pacotes ADD COLUMN cor varchar(7) NOT NULL DEFAULT '#64748B';
