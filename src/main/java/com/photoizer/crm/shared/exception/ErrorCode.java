package com.photoizer.crm.shared.exception;

/**
 * PATTERN: Type-Safe Enum for Error Codes
 *
 * Centraliza todos os códigos de erro de negócio em um único enum.
 * Permite que clientes da API identifiquem erros programaticamente
 * (anti-correlação entre frontend e backend) e facilita i18n futura.
 *
 * Cada constante corresponde a uma exceção de domínio. O GlobalExceptionHandler
 * mapeia automaticamente o ErrorCode para o status HTTP via a hierarquia
 * BusinessException → {NotFound, Conflict, Unprocessable, ...}Exception.
 *
 * Uso: throw new NotFoundException(ErrorCode.CLIENTE_NAO_ENCONTRADO, "ID: " + id);
 */
public enum ErrorCode {

    // ─── Cliente ───────────────────────────────────────────────
    CLIENTE_NAO_ENCONTRADO,
    CLIENTE_DUPLICADO,

    // ─── Agendamento ───────────────────────────────────────────
    AGENDAMENTO_NAO_ENCONTRADO,
    AGENDAMENTO_NO_PASSADO,
    AGENDAMENTO_NAO_PERMITIDO_PARA_UPLOAD,
    CONFLITO_DE_AGENDA,
    ENSAIO_NAO_FINALIZADO,
    COMPROVANTE_OBRIGATORIO,
    PAGAMENTO_INSUFICIENTE,
    STATUS_AGENDAMENTO_INVALIDO,
    EDITOR_NAO_ENCONTRADO,
    FOTOGRAFO_NAO_ENCONTRADO,
    FOTOGRAFO_COM_ENSAIOS_VINCULADOS,

    // ─── Pacote ────────────────────────────────────────────────
    PACOTE_NAO_ENCONTRADO,
    PACOTE_INATIVO,

    // ─── Edição ────────────────────────────────────────────────
    EDICAO_NAO_ENCONTRADA,
    FOTO_EDICAO_NAO_ENCONTRADA,
    FOTO_SEM_RAW,
    STATUS_EDICAO_INVALIDO,
    EDICAO_ERRO_NEGOCIO,

    // ─── E-commerce ────────────────────────────────────────────
    GALERIA_NAO_ENCONTRADA,
    COMPRA_NAO_ENCONTRADA,
    FOTO_NAO_ENCONTRADA,
    CARRINHO_VAZIO,
    FOTO_JA_SELECIONADA,
    FOTO_JA_BAIXADA,
    LIMITE_PACOTE_EXCEDIDO,
    COMPRA_JA_PAGA,
    SESSAO_INVALIDA,
    FOTO_INDISPONIVEL,
    TOKEN_EXPIRADO,

    // ─── Contrato ──────────────────────────────────────────────
    CONTRATO_NAO_ENCONTRADO,
    CONTRATO_ESTADO_INVALIDO,
    CONTRATO_TOKEN_EXPIRADO,

    // ─── Documento ─────────────────────────────────────────────
    TIPO_COMPROVANTE_INVALIDO,

    // ─── Foto ──────────────────────────────────────────────────
    FOTO_ENSAIO_NAO_ENCONTRADA,
    FOTO_NAO_PERTENCE_AO_AGENDAMENTO,
    STATUS_FOTO_INVALIDO,

    // ─── Financeiro ────────────────────────────────────────────
    AGENDAMENTO_NAO_ENCONTRADO_PARA_FINANCEIRO,
    PAGAMENTO_NAO_ENCONTRADO,
    RECEITA_NAO_ENCONTRADA,
    PACOTE_NAO_ENCONTRADO_PARA_PREVIEW,
    OPERACAO_NAO_PERMITIDA,
    VALOR_INVALIDO,
    INDICADOR_INVALIDO,
    CLIENTE_OBRIGATORIO,
    VALOR_RECEBIDO_EXCEDE_FINAL,

    // ─── Despesa ───────────────────────────────────────────────
    DESPESA_NAO_ENCONTRADA,
    CATEGORIA_DESPESA_NAO_ENCONTRADA,
    CATEGORIA_DUPLICADA,
    CATEGORIA_EM_USO,
    CATEGORIA_OBRIGATORIA,
    DESPESA_RECORRENTE_NAO_PAGA,
    STATUS_DESPESA_INVALIDO,
    AGENDAMENTO_VINCULADO_INVALIDO,

    // ─── Indicador ─────────────────────────────────────────────
    INDICADOR_NAO_ENCONTRADO,
    INDICADOR_DUPLICADO,

    // ─── Notificação ───────────────────────────────────────────
    NOTIFICACAO_NAO_ENCONTRADA,
    NOTIFICACAO_NAO_PERTENCE_AO_USUARIO,
    NOTIFICACAO_ERRO_NEGOCIO,

    // ─── Config ────────────────────────────────────────────────
    CONFIGURACAO_INVALIDA,

    // ─── Fotógrafo ─────────────────────────────────────────────
    FOTOGRAFO_COM_ENSAIOS,

    // ─── Auth ──────────────────────────────────────────────────
    CREDENCIAIS_INVALIDAS,
    ACESSO_NEGADO,

    // ─── Genéricos ─────────────────────────────────────────────
    ARGUMENTO_INVALIDO,
    INTEGRIDADE_VIOLADA,
    ERRO_INTERNO
}
