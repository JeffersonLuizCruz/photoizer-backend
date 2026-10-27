-- Histórico diário do consumo de armazenamento.
--
-- Uma linha por dia (a data é a chave, o que torna a coleta idempotente).
-- Guarda apenas agregados; nenhum caminho de arquivo é exposto.

CREATE TABLE armazenamento_snapshots (
    data                   date         PRIMARY KEY,
    bytes_fotos            bigint       NOT NULL,
    bytes_derivados        bigint       NOT NULL,
    bytes_editadas         bigint       NOT NULL,
    bytes_documentos       bigint       NOT NULL,
    bytes_propostas        bigint       NOT NULL,
    bytes_outros           bigint       NOT NULL,
    bytes_total            bigint       NOT NULL,
    volume_total           bigint       NOT NULL,
    volume_livre           bigint       NOT NULL,
    total_fotos            integer      NOT NULL,
    total_galerias         integer      NOT NULL,
    galerias_arquivadas    integer      NOT NULL,
    fotos_arquivadas       integer      NOT NULL,
    bytes_liberados_no_dia bigint       NOT NULL DEFAULT 0,
    coletado_em            timestamp(6) NOT NULL
);

CREATE INDEX idx_armazenamento_snapshots_data
    ON armazenamento_snapshots (data);
