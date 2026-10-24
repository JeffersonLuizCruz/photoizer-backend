package com.photoizer.crm.shared.logging;

/**
 * Chaves canônicas do MDC.
 *
 * <p>Use sempre estas constantes ao publicar contexto no {@link org.slf4j.MDC},
 * para que os campos do JSON (ELK/Datadog) permaneçam consistentes.
 */
public final class LoggingKeys {

    /** ID de correlação da requisição (reutilizado do header X-Correlation-ID). */
    public static final String CORRELATION_ID = "correlationId";

    /** UUID interno do usuário autenticado — nunca e-mail, CPF ou username. */
    public static final String USER_ID = "userId";

    /** Papéis do usuário autenticado (ex.: ROLE_ADMIN,ROLE_AGENDADOR). */
    public static final String USER_ROLE = "userRole";

    /** Reservado para quando houver multi-tenant (claim do JWT). */
    public static final String TENANT_ID = "tenantId";

    private LoggingKeys() {
    }
}
