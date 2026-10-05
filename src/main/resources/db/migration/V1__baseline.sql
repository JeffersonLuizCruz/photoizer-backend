-- V1 - Baseline do schema do CRM Photoizer (PostgreSQL).
-- Gerado a partir das entidades JPA (Hibernate 7, dialect PostgreSQL, naming
-- strategy snake_case do Spring Boot) via com.photoizer.crm.tools.SchemaBaselineGenerator
-- (mvn -Dtest=SchemaBaselineGenerator test).
-- NAO editar esta migracao apos aplicada em qualquer ambiente; alteracoes
-- devem entrar em novas migracoes V2__, V3__, ...
    create table agendamento_fotografos (
        id uuid not null,
        created_at timestamp(6) not null,
        created_by varchar(255) not null,
        updated_at timestamp(6) not null,
        data_pagamento timestamp(6),
        papel_parceiro varchar(20) check ((papel_parceiro in ('ADMIN','FOTOGRAFO','EDITOR','AGENDADOR'))),
        percentual numeric(5,2),
        status varchar(20) not null check ((status in ('PENDENTE','PAGO','CANCELADO'))),
        tipo_valor varchar(20) check ((tipo_valor in ('FIXO','PERCENTUAL'))),
        valor_repassar numeric(10,2) not null,
        agendamento_id uuid not null,
        fotografo_id uuid not null,
        primary key (id),
        unique (agendamento_id, fotografo_id)
    );
    create table agendamento_reatribuicoes (
        id uuid not null,
        created_at timestamp(6) not null,
        created_by varchar(255) not null,
        updated_at timestamp(6) not null,
        motivo TEXT,
        agendamento_id uuid not null,
        fotografo_anterior_id uuid,
        novo_fotografo_id uuid not null,
        solicitante_id uuid,
        primary key (id)
    );
    create table agendamentos (
        id uuid not null,
        assinante_nome varchar(64),
        created_at timestamp(6) not null,
        created_by varchar(255) not null,
        updated_at timestamp(6) not null,
        autoriza_uso_imagem boolean not null,
        clausulas_personalizadas TEXT,
        custo_deslocamento numeric(10,2),
        data_assinatura timestamp(6),
        data_confirmacao timestamp(6),
        data_envio_proposta timestamp(6),
        data_finalizacao timestamp(6),
        data_hora_ensaio timestamp(6) not null,
        data_pagamento_final timestamp(6),
        data_realizacao timestamp(6),
        data_recusa timestamp(6),
        duracao_minutos integer not null,
        ensaio_destaque boolean not null,
        forma_pagamento_final varchar(20) check ((forma_pagamento_final in ('PIX','CARTAO','DINHEIRO','TRANSFERENCIA','OUTRO'))),
        indicador_id uuid,
        indicador_nome varchar(150),
        indicador_telefone varchar(30),
        local_ensaio varchar(255) not null,
        motivo_recusa varchar(500),
        observacoes TEXT,
        percentual_entrada numeric(5,2),
        recusado_por varchar(150),
        repassar_deslocamento boolean,
        snapshot_hash varchar(64),
        snapshot_json TEXT,
        status varchar(40) not null check ((status in ('PRE_RESERVA','AGUARDANDO_APROVACAO','PAGAMENTO_CONFIRMADO','CONFIRMADO','REALIZADO','AGUARDANDO_PAGAMENTO_FINAL','EM_EDICAO','FINALIZADO','CANCELADO','NO_SHOW'))),
        taxa_deslocamento numeric(10,2) not null,
        token_expiracao timestamp(6),
        token_galeria uuid not null unique,
        token_proposta varchar(128),
        token_proposta_expiracao timestamp(6),
        token_proposta_hash varchar(64) unique,
        url_assinatura_imagem varchar(500),
        url_comprovante_entrada varchar(500),
        url_comprovante_final varchar(500),
        url_pdf_assinatura varchar(500),
        valor_entrada_exigido numeric(10,2) not null,
        valor_entrada_pago numeric(10,2) not null,
        valor_extras numeric(10,2) not null,
        valor_lucro_crm numeric(10,2),
        valor_partilha_fotografo numeric(10,2),
        valor_restante numeric(10,2) not null,
        valor_total numeric(10,2) not null,
        valor_total_final numeric(10,2) not null,
        cliente_id uuid,
        editor_id uuid,
        fotografo_id uuid,
        pacote_id uuid not null,
        primary key (id)
    );
    create table assinaturas (
        id uuid not null,
        agendamento_id uuid not null unique,
        created_at timestamp(6) not null,
        created_by varchar(255) not null,
        updated_at timestamp(6) not null,
        data_assinatura timestamp(6) not null,
        fuso_horario varchar(100),
        hash varchar(64),
        ip varchar(45),
        nome_assinante varchar(255) not null,
        plataforma varchar(150),
        url_assinatura_imagem varchar(500),
        user_agent varchar(500),
        primary key (id)
    );
    create table avaliacoes (
        id uuid not null,
        agendamento_id uuid,
        aprovado boolean,
        created_at timestamp(6) not null,
        created_by varchar(255) not null,
        updated_at timestamp(6) not null,
        cliente_id uuid not null,
        comentario TEXT,
        depoimento boolean,
        pacote_id uuid,
        pontuacao integer not null,
        primary key (id)
    );
    create table clientes (
        id uuid not null,
        created_at timestamp(6) not null,
        created_by varchar(255) not null,
        updated_at timestamp(6) not null,
        cidade varchar(100),
        cpf varchar(14) unique,
        data_cadastro timestamp(6),
        email varchar(255),
        estado varchar(2),
        nome varchar(255) not null,
        observacoes TEXT,
        origem varchar(20) not null check ((origem in ('INDICACAO','ANUNCIO','OUTROS'))),
        preferencias TEXT,
        senha_hash varchar(255),
        telefone varchar(20) not null unique,
        primary key (id)
    );
    create table compras_extras (
        id uuid not null,
        agendamento_id uuid not null,
        created_at timestamp(6) not null,
        created_by varchar(255) not null,
        updated_at timestamp(6) not null,
        data_pagamento timestamp(6),
        metodo_pagamento varchar(20) check ((metodo_pagamento in ('PIX','TRANSFERENCIA','DINHEIRO'))),
        motivo_recusa TEXT,
        observacao TEXT,
        quantidade_fotos integer,
        status varchar(30) not null check ((status in ('AGUARDANDO_COMPROVANTE','AGUARDANDO_CONFIRMACAO','PAGA','CANCELADA'))),
        url_comprovante varchar(500),
        valor_total numeric(10,2) not null,
        primary key (id)
    );
    create table configuracoes (
        chave varchar(100) not null,
        valor TEXT,
        primary key (chave)
    );
    create table despesas (
        id uuid not null,
        agendamento_id uuid,
        created_at timestamp(6) not null,
        created_by varchar(255) not null,
        updated_at timestamp(6) not null,
        categoria varchar(100) not null,
        data date not null,
        data_pagamento timestamp(6),
        data_proxima_geracao date,
        descricao varchar(255) not null,
        forma_pagamento varchar(20) check ((forma_pagamento in ('PIX','CARTAO','DINHEIRO','TRANSFERENCIA','OUTRO'))),
        fotografo_id uuid,
        gerada_de_id uuid,
        observacao TEXT,
        recorrencia varchar(20) not null check ((recorrencia in ('UNICA','MENSAL','ANUAL'))),
        status varchar(20) not null check ((status in ('PAGO','PENDENTE','RECORRENTE'))),
        url_comprovante varchar(500),
        valor numeric(10,2) not null,
        categoria_id uuid,
        primary key (id)
    );
    create table despesas_categorias (
        id uuid not null,
        ativo boolean not null,
        created_at timestamp(6) not null,
        created_by varchar(255) not null,
        updated_at timestamp(6) not null,
        cor varchar(20),
        nome varchar(100) not null unique,
        ordem integer,
        primary key (id)
    );
    create table extras_servico (
        id uuid not null,
        created_at timestamp(6) not null,
        created_by varchar(255) not null,
        updated_at timestamp(6) not null,
        quantidade integer not null,
        tipo varchar(10) not null check ((tipo in ('FOTO','VIDEO'))),
        valor_total numeric(10,2) not null,
        valor_unitario numeric(10,2) not null,
        agendamento_id uuid not null,
        primary key (id)
    );
    create table favoritos (
        id uuid not null,
        agendamento_id uuid not null,
        created_at timestamp(6) not null,
        created_by varchar(255) not null,
        updated_at timestamp(6) not null,
        foto_id uuid not null,
        session_id uuid not null,
        primary key (id),
        unique (session_id, foto_id)
    );
    create table foto_comentarios (
        id uuid not null,
        agendamento_id uuid not null,
        created_at timestamp(6) not null,
        created_by varchar(255) not null,
        updated_at timestamp(6) not null,
        autor_nome varchar(120),
        foto_id uuid not null,
        lida boolean not null,
        mensagem TEXT not null,
        origem varchar(10) not null check ((origem in ('CLIENTE','STAFF'))),
        primary key (id)
    );
    create table foto_ensaio_tags (
        foto_ensaio_id uuid not null,
        tags varchar(255)
    );
    create table fotos_ensaio (
        id uuid not null,
        agendamento_id uuid not null,
        created_at timestamp(6) not null,
        created_by varchar(255) not null,
        updated_at timestamp(6) not null,
        categoria varchar(50),
        compra_extra_id uuid,
        data_download timestamp(6),
        data_sessao date,
        descricao TEXT,
        destaque boolean not null,
        file_name varchar(255) not null,
        foto_edicao_id uuid,
        metadata_exif TEXT,
        ordem integer not null,
        original_path varchar(500) not null,
        selecionada_pacote boolean not null,
        status varchar(25) not null check ((status in ('INEDITA','PUBLICADA','AGUARDANDO_COMPROVANTE','AGUARDANDO_CONFIRMACAO','PAGA'))),
        thumb_path varchar(500) not null,
        titulo varchar(255),
        visivel boolean,
        watermarked_path varchar(500) not null,
        primary key (id)
    );
    create table fotos_extras (
        id uuid not null,
        created_at timestamp(6) not null,
        created_by varchar(255) not null,
        updated_at timestamp(6) not null,
        quantidade integer not null,
        valor_total numeric(10,2) not null,
        valor_unitario numeric(10,2) not null,
        agendamento_id uuid not null,
        primary key (id)
    );
    create table indicacoes (
        id uuid not null,
        agendamento_id uuid not null,
        created_at timestamp(6) not null,
        created_by varchar(255) not null,
        updated_at timestamp(6) not null,
        data_pagamento timestamp(6),
        indicador_id uuid,
        indicador_nome varchar(255) not null,
        indicador_telefone varchar(20) not null,
        origem varchar(20) not null check ((origem in ('PACOTE','INDICADOR','FOTO_EXTRA','VIDEO_EXTRA'))),
        percentual numeric(5,2) not null,
        status varchar(20) not null check ((status in ('PENDENTE','PAGA','CANCELADA'))),
        valor_comissao numeric(10,2) not null,
        valor_referencia numeric(10,2) not null,
        primary key (id)
    );
    create table indicadores (
        id uuid not null,
        created_at timestamp(6) not null,
        created_by varchar(255) not null,
        updated_at timestamp(6) not null,
        nome varchar(255) not null,
        observacoes TEXT,
        percentual_comissao numeric(5,2),
        telefone varchar(20) not null,
        primary key (id),
        unique (nome, telefone)
    );
    create table itens_carrinho (
        id uuid not null,
        agendamento_id uuid not null,
        created_at timestamp(6) not null,
        created_by varchar(255) not null,
        updated_at timestamp(6) not null,
        foto_id uuid not null,
        session_id uuid not null,
        primary key (id)
    );
    create table notificacoes (
        id uuid not null,
        created_at timestamp(6) not null,
        lida boolean not null,
        link varchar(255),
        mensagem varchar(500) not null,
        tipo varchar(30) not null check ((tipo in ('NOVO_ENSAIO','ENSAIO_REALIZADO','PAGAMENTO_FINAL','ENSAIO_REATRIBUIDO'))),
        titulo varchar(100) not null,
        user_id uuid not null,
        primary key (id)
    );
    create table pacotes (
        id uuid not null,
        ativo boolean not null,
        created_at timestamp(6) not null,
        created_by varchar(255) not null,
        updated_at timestamp(6) not null,
        beneficios TEXT,
        bloqueia_dia_inteiro boolean not null,
        descricao TEXT,
        dias_para_entrega integer,
        duracao_estimada varchar(50),
        imagem_capa varchar(500),
        nome varchar(100) not null,
        preco_foto_extra numeric(10,2) not null,
        quantidade_fotos integer not null,
        quantidade_videos integer not null,
        valor_base numeric(10,2) not null,
        primary key (id)
    );
    create table pagamentos (
        id uuid not null,
        created_at timestamp(6) not null,
        created_by varchar(255) not null,
        updated_at timestamp(6) not null,
        compra_extra_id uuid,
        data_pagamento timestamp(6) not null,
        observacao varchar(255),
        url_comprovante varchar(500),
        valor numeric(10,2) not null,
        agendamento_id uuid not null,
        primary key (id)
    );
    create table receitas (
        id uuid not null,
        agendamento_id uuid,
        created_at timestamp(6) not null,
        created_by varchar(255) not null,
        updated_at timestamp(6) not null,
        cliente_id uuid not null,
        cliente_nome varchar(255) not null,
        data_previsao_recebimento date,
        data_recebimento_real timestamp(6),
        descricao varchar(255),
        forma_pagamento varchar(20) check ((forma_pagamento in ('PIX','CARTAO','DINHEIRO','TRANSFERENCIA','OUTRO'))),
        observacoes TEXT,
        status varchar(20) not null check ((status in ('PAGO_TOTAL','PAGO_PARCIAL','PENDENTE','CANCELADO'))),
        tipo_servico varchar(20) not null check ((tipo_servico in ('ENSAIO','CASAMENTO','EVENTO','PRODUTO','OUTRO'))),
        valor_bruto numeric(10,2) not null,
        valor_comissao numeric(10,2) not null,
        valor_final numeric(10,2) not null,
        valor_recebido numeric(10,2) not null,
        primary key (id)
    );
    create table refresh_tokens (
        id uuid not null,
        created_at timestamp(6) with time zone not null,
        expires_at timestamp(6) with time zone not null,
        family_id uuid not null,
        revoked_at timestamp(6) with time zone,
        token_hash varchar(64) not null unique,
        used_at timestamp(6) with time zone,
        user_id uuid not null,
        primary key (id)
    );
    create table sessoes (
        id uuid not null,
        created_at timestamp(6) not null,
        created_by varchar(255) not null,
        updated_at timestamp(6) not null,
        cliente_id uuid,
        data_realizacao date,
        descricao TEXT,
        local varchar(255),
        nome_sessao varchar(255) not null,
        status varchar(20) not null check ((status in ('ATIVA','FINALIZADA','CANCELADA'))),
        primary key (id)
    );
    create table token_blocklist (
        id uuid not null,
        blocked_at timestamp(6) with time zone not null,
        expires_at timestamp(6) with time zone not null,
        jti varchar(255) not null unique,
        primary key (id)
    );
    create table users (
        id uuid not null,
        ativo boolean not null,
        created_at timestamp(6) not null,
        created_by varchar(255) not null,
        updated_at timestamp(6) not null,
        email varchar(100) not null unique,
        nome varchar(100) not null,
        papel varchar(20) not null check ((papel in ('ADMIN','FOTOGRAFO','EDITOR','AGENDADOR'))),
        password varchar(255) not null,
        telefone varchar(20),
        primary key (id)
    );
    create table videos_extras (
        id uuid not null,
        created_at timestamp(6) not null,
        created_by varchar(255) not null,
        updated_at timestamp(6) not null,
        quantidade integer not null,
        valor_total numeric(10,2) not null,
        valor_unitario numeric(10,2) not null,
        agendamento_id uuid not null,
        primary key (id)
    );
    create index IDXh0cy1smgmvbrawbl0u7g1sg2 
       on agendamento_reatribuicoes (agendamento_id);
    create index IDXr6cu6iq5mfed8b84lr7nvpilg 
       on agendamentos (data_hora_ensaio);
    create index IDX3g3t2eyk96lqx5mhx0qntdi7q 
       on agendamentos (status);
    create index IDXtm4u5x1lf5glipyp253i4e421 
       on agendamentos (cliente_id);
    alter table if exists agendamento_fotografos 
       add constraint FK8xwh9049bp2uwfagcd46m4aur 
       foreign key (agendamento_id) 
       references agendamentos;
    alter table if exists agendamento_fotografos 
       add constraint FK7rqy1mb1361jj8fabxognndid 
       foreign key (fotografo_id) 
       references users;
    alter table if exists agendamento_reatribuicoes 
       add constraint FKhqc04ec1inspv8dp6eaq0egv7 
       foreign key (agendamento_id) 
       references agendamentos;
    alter table if exists agendamento_reatribuicoes 
       add constraint FKnvpgyk6o36o285ex3cfb4lo0s 
       foreign key (fotografo_anterior_id) 
       references users;
    alter table if exists agendamento_reatribuicoes 
       add constraint FK13qdubwk6iop321ov40t70roe 
       foreign key (novo_fotografo_id) 
       references users;
    alter table if exists agendamento_reatribuicoes 
       add constraint FK4w48821y35l0b4noe7k61w6j3 
       foreign key (solicitante_id) 
       references users;
    alter table if exists agendamentos 
       add constraint FK7j02h7ufegbom5ke8ydlafx0x 
       foreign key (cliente_id) 
       references clientes;
    alter table if exists agendamentos 
       add constraint FKfixllaxsfqelki7np5q08u4ty 
       foreign key (editor_id) 
       references users;
    alter table if exists agendamentos 
       add constraint FKb5iekd0oaitl9ai9bwg9eh2st 
       foreign key (fotografo_id) 
       references users;
    alter table if exists agendamentos 
       add constraint FKdmttrdw51kbwo0mu69cnwun0v 
       foreign key (pacote_id) 
       references pacotes;
    alter table if exists despesas 
       add constraint FK1nwibqjtce2vjs81arrqtivqq 
       foreign key (categoria_id) 
       references despesas_categorias;
    alter table if exists extras_servico 
       add constraint FKa76u5smcerxoxs752eeh8wghr 
       foreign key (agendamento_id) 
       references agendamentos;
    alter table if exists foto_ensaio_tags 
       add constraint FKkpatgaf3vavnowt17s3v74w6k 
       foreign key (foto_ensaio_id) 
       references fotos_ensaio;
    alter table if exists fotos_extras 
       add constraint FK33uv00n4hyr7lqn9cgu4t4yhi 
       foreign key (agendamento_id) 
       references agendamentos;
    alter table if exists pagamentos 
       add constraint FK5p05t9nq2xegdii41ng712t12 
       foreign key (agendamento_id) 
       references agendamentos;
    alter table if exists videos_extras 
       add constraint FKekn7ovtoua2tsn8c2j4t1xtd2 
       foreign key (agendamento_id) 
       references agendamentos;