package com.photoizer.crm.shared.exception;

import com.photoizer.crm.shared.logging.SensitiveDataMask;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Substitui o {@code response.sendError} (que devolvia o body padrão do container)
 * por um {@link ErrorResponse} com {@code correlationId}.
 *
 * <p>Também é reutilizado pelo {@code JwtAuthenticationFilter} (token revogado) e
 * pelo {@code CsrfCookieFilter} (CSRF inválido), garantindo o mesmo contrato de
 * erro para todas as rejeições que acontecem fora do DispatcherServlet.
 */
@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(SecurityErrorHandler.class);

    private final ObjectMapper objectMapper;

    public SecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        // 401 é rotina (token expirado); o access log já registra. Não poluir com WARN.
        log.debug("[401] {} {} - {}",
            request.getMethod(), caminho(request), SensitiveDataMask.mask(ex.getMessage()));
        escrever(response, HttpStatus.UNAUTHORIZED, "Não autenticado");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        log.warn("[403] {} {} - {}",
            request.getMethod(), caminho(request), SensitiveDataMask.mask(ex.getMessage()));
        escrever(response, HttpStatus.FORBIDDEN, "Acesso negado");
    }

    /** Usado também por filtros que rejeitam a requisição antes do DispatcherServlet. */
    public void escrever(HttpServletResponse response, HttpStatus status, String mensagem)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(),
            new ErrorResponse(status.value(), status.getReasonPhrase(), mensagem));
    }

    private String caminho(HttpServletRequest request) {
        return SensitiveDataMask.mask(request.getRequestURI());
    }
}
