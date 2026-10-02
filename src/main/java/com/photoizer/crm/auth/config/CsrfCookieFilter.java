package com.photoizer.crm.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Proteção CSRF via double-submit cookie (achado A3).
 *
 * <p>Quando a autenticação vem por cookie (não por header {@code Authorization}),
 * requisições que mudam estado (POST/PUT/PATCH/DELETE) precisam ecoar o valor do
 * cookie {@code XSRF-TOKEN} no header {@code X-XSRF-TOKEN}. Um site malicioso não
 * consegue ler o cookie (SameSite + origem), então não forja o par.
 *
 * <p>Requisições autenticadas por Bearer ficam isentas (não são vulneráveis a CSRF,
 * pois dependem de header que o navegador não envia automaticamente).
 * Endpoints de login/registro/refresh/sessão pública são isentos por serem
 * pré-autenticação.
 */
@Component
@Order(10)
public class CsrfCookieFilter extends OncePerRequestFilter {

    private static final Set<String> METODOS_SEGUROS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final Set<String> PATHS_ISENTOS = Set.of(
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        "/api/v1/auth/logout",
        "/api/v1/auth/cliente/login",
        "/api/v1/auth/cliente/registro",
        "/api/v1/ecommerce/sessao"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (METODOS_SEGUROS.contains(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        var path = request.getRequestURI();
        if (PATHS_ISENTOS.stream().anyMatch(path::startsWith)) {
            chain.doFilter(request, response);
            return;
        }

        // Se autentica via Bearer, não há risco de CSRF (header não automático).
        var authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        // Sem cookie de sessão não há o que proteger (ex.: galeria pública).
        var csrfCookie = lerCookie(request, AuthCookieService.CSRF_COOKIE);
        var accessCookie = lerCookie(request, AuthCookieService.ACCESS_COOKIE);
        if (accessCookie == null) {
            chain.doFilter(request, response);
            return;
        }

        var csrfHeader = request.getHeader(AuthCookieService.CSRF_HEADER);
        if (csrfCookie == null || csrfHeader == null || !constantTimeEquals(csrfCookie, csrfHeader)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                "{\"status\":403,\"error\":\"Forbidden\",\"message\":\"CSRF token inválido ou ausente\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private String lerCookie(HttpServletRequest request, String nome) {
        if (request.getCookies() == null) {
            return null;
        }
        for (var cookie : request.getCookies()) {
            if (nome.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8),
            b.getBytes(StandardCharsets.UTF_8));
    }
}
