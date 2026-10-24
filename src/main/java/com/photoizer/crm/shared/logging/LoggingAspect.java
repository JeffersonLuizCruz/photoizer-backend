package com.photoizer.crm.shared.logging;

import jakarta.annotation.PostConstruct;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Logging de diagnóstico das camadas de serviço e repositório.
 *
 * <p>O log HTTP (método, URL, status e latência) <strong>não</strong> fica aqui:
 * é responsabilidade do {@link RequestLoggingFilter}, que enxerga o status final
 * (inclusive 401/403/429 emitidos pela SecurityFilterChain) e não é afetado pelo
 * mapeamento de exceções do {@code GlobalExceptionHandler}.
 *
 * <p>Níveis:
 * <ul>
 *   <li>services: DEBUG (args mascarados + duração);</li>
 *   <li>repositories: TRACE;</li>
 *   <li>erros: DEBUG aqui e ERROR/stack trace somente no GlobalExceptionHandler
 *       (evita stack trace duplicado).</li>
 * </ul>
 */
@Aspect
@Component
public class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    /**
     * Pacotes cujos argumentos nunca devem ser logados (credenciais/PII).
     * Ex.: DTOs de login carregam senha em texto plano.
     */
    private static final List<String> PACOTES_SEM_LOG_DE_ARGS = List.of(
        "com.photoizer.crm.auth",
        "com.photoizer.crm.cliente.service.ClienteAuthService"
    );

    @PostConstruct
    void init() {
        log.debug("LoggingAspect inicializado (AOP ativo)");
    }

    @Pointcut("within(org.springframework.stereotype.Service+)")
    public void serviceLayer() {
    }

    @Pointcut("within(org.springframework.stereotype.Repository+)")
    public void repositoryLayer() {
    }

    @Pointcut("within(com.photoizer.crm.shared.logging..*)")
    public void loggingLayer() {
    }

    @Around("serviceLayer() && !loggingLayer()")
    public Object logService(ProceedingJoinPoint joinPoint) throws Throwable {
        long inicio = System.nanoTime();
        var metodo = joinPoint.getSignature().toShortString();

        // Guarda obrigatória: mascarar args é caro e não deve rodar com DEBUG desligado.
        if (log.isDebugEnabled()) {
            var declaracao = joinPoint.getSignature().getDeclaringTypeName();
            var args = PACOTES_SEM_LOG_DE_ARGS.stream().anyMatch(declaracao::startsWith)
                ? "[REDACTED]"
                : SensitiveDataMask.maskArgs(joinPoint.getArgs());
            log.debug("[SERVICE] {} args=[{}]", metodo, args);
        }

        try {
            var resultado = joinPoint.proceed();
            if (log.isDebugEnabled()) {
                log.debug("[SERVICE] {} -> {} ({}ms)",
                    metodo, getResultSummary(resultado), millis(inicio));
            }
            return resultado;
        } catch (Throwable t) {
            // O ERROR/stack trace é responsabilidade do GlobalExceptionHandler.
            if (log.isDebugEnabled()) {
                log.debug("[SERVICE] {} falhou após {}ms: {}",
                    metodo, millis(inicio), t.getClass().getSimpleName());
            }
            throw t;
        }
    }

    @Around("repositoryLayer() && !loggingLayer()")
    public Object logRepository(ProceedingJoinPoint joinPoint) throws Throwable {
        long inicio = System.nanoTime();
        var metodo = joinPoint.getSignature().toShortString();

        try {
            var resultado = joinPoint.proceed();
            if (log.isTraceEnabled()) {
                log.trace("[REPOSITORY] {} ({}ms)", metodo, millis(inicio));
            }
            return resultado;
        } catch (Throwable t) {
            if (log.isTraceEnabled()) {
                log.trace("[REPOSITORY] {} falhou após {}ms: {}",
                    metodo, millis(inicio), t.getClass().getSimpleName());
            }
            throw t;
        }
    }

    private long millis(long inicioNanos) {
        return (System.nanoTime() - inicioNanos) / 1_000_000;
    }

    private String getResultSummary(Object result) {
        if (result == null) {
            return "void";
        }
        if (result instanceof Collection<?> c) {
            return c.size() + " result(s)";
        }
        if (result instanceof Optional<?> o) {
            return o.isPresent() ? "present" : "empty";
        }
        if (result instanceof String s) {
            return s.length() > 100 ? s.substring(0, 100) + "..." : s;
        }
        if (result instanceof ResponseEntity<?> re) {
            return "ResponseEntity[" + re.getStatusCode().value() + "]";
        }
        return result.getClass().getSimpleName();
    }
}
