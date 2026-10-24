package com.photoizer.crm.shared.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Publica o usuário autenticado no MDC.
 *
 * <p>Precisa rodar <strong>depois</strong> do JwtAuthenticationFilter (ver
 * {@code SecurityConfig}), pois é ele quem popula o SecurityContext. Só publica
 * o UUID interno — nunca e-mail, CPF ou username, que são PII.
 *
 * <p>Não limpa o MDC: o {@link CorrelationIdFilter} limpa ao final da requisição,
 * pois o access log ({@link RequestLoggingFilter}) ainda precisa do userId.
 */
@Component
public class UserMdcFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof UUID userId) {
            MDC.put(LoggingKeys.USER_ID, userId.toString());

            var roles = auth.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .toList();
            if (!roles.isEmpty()) {
                MDC.put(LoggingKeys.USER_ROLE, String.join(",", roles));
            }

            // Quando houver multi-tenant: MDC.put(LoggingKeys.TENANT_ID, claimTenant);
        }

        chain.doFilter(request, response);
        // NÃO remover aqui — ver Javadoc.
    }
}
