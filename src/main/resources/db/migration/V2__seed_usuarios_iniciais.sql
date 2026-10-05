-- V2 - Usuarios iniciais do sistema, semeados em TODOS os ambientes
-- (dev, homolog e prod) para que o banco nasca com ADMIN e AGENDADOR.
--
-- Senha padrao dos dois usuarios: dev123 (hash BCrypt abaixo).
-- TROCAR A SENHA APOS O PRIMEIRO ACESSO (ainda nao existe fluxo de troca de
-- senha na aplicacao; a troca exige um endpoint novo ou UPDATE no banco).
--
-- O e-mail do agendador e 'agendamento@photoizer.com' de proposito: o
-- LegacySeedPartnerCleanupSeeder remove/desativa 'agendador@photoizer.com'
-- em todos os profiles.
--
-- Idempotente: ON CONFLICT (email) DO NOTHING preserva usuarios ja existentes
-- (ex.: admin@photoizer.com criado por versoes anteriores em dev).

INSERT INTO users (id, ativo, created_at, created_by, updated_at, email, nome, papel, password, telefone)
VALUES (gen_random_uuid(), true, now(), 'SYSTEM', now(),
        'admin@photoizer.com', 'Administrador', 'ADMIN',
        '$2a$10$S3gNC/tkk.ITiHVabcV/JuiTE7m7aWdYngKf7Xo89p2l13EySWbhC', NULL)
ON CONFLICT (email) DO NOTHING;

INSERT INTO users (id, ativo, created_at, created_by, updated_at, email, nome, papel, password, telefone)
VALUES (gen_random_uuid(), true, now(), 'SYSTEM', now(),
        'agendamento@photoizer.com', 'Agendador', 'AGENDADOR',
        '$2a$10$S3gNC/tkk.ITiHVabcV/JuiTE7m7aWdYngKf7Xo89p2l13EySWbhC', NULL)
ON CONFLICT (email) DO NOTHING;
