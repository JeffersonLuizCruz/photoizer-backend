-- Remove o sistema de favoritos/curtidas da galeria.
-- ATENÇÃO: migração destrutiva. Faça backup da tabela `favoritos` se os dados
-- históricos forem necessários antes de aplicar em produção.

drop table if exists favoritos;
