package com.photoizer.crm.auth.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * Gerencia a emissão/leitura de cookies de autenticação (achado A3).
 *
 * <p>O access token passa a ser entregue em cookie {@code HttpOnly}, não acessível
 * a JavaScript, reduzindo o impacto de XSS. O refresh token usa um cookie restrito
 * ao path de refresh. Para proteção CSRF (double-submit cookie), um token aleatório
 * legível é emitido no cookie {@code XSRF-TOKEN} e deve ser ecoado pelo cliente no
 * header {@code X-XSRF-TOKEN}.
 *
 * <p>Backward compatibility: o client ainda pode enviar {@code Authorization: Bearer}
 * (o frontend será migrado, mas integrações existentes seguem funcionando).
 */
@Component
public class AuthCookieService {

    public static final String ACCESS_COOKIE = "photoizer_access";
    public static final String REFRESH_COOKIE = "photoizer_refresh";
    public static final String CSRF_COOKIE = "XSRF-TOKEN";
    public static final String CSRF_HEADER = "X-XSRF-TOKEN";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final boolean secure;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public AuthCookieService(
            @Value("${app.security.cookie.secure:false}") boolean secure,
            @Value("${app.jwt.expiration}") long accessExpirationMs,
            @Value("${app.jwt.refresh-expiration}") long refreshExpirationMs) {
        this.secure = secure;
        this.accessTtl = Duration.ofMillis(accessExpirationMs);
        this.refreshTtl = Duration.ofMillis(refreshExpirationMs);
    }

    public void emitirCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        response.addHeader(HttpHeaders.SET_COOKIE,
            build(ACCESS_COOKIE, accessToken, accessTtl, "/", true));
        response.addHeader(HttpHeaders.SET_COOKIE,
            build(REFRESH_COOKIE, refreshToken, refreshTtl, "/api/v1/auth", true));
        response.addHeader(HttpHeaders.SET_COOKIE,
            build(CSRF_COOKIE, gerarCsrfToken(), refreshTtl, "/", false));
    }

    public void limparCookies(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(ACCESS_COOKIE, "", Duration.ZERO, "/", true));
        response.addHeader(HttpHeaders.SET_COOKIE, build(REFRESH_COOKIE, "", Duration.ZERO, "/api/v1/auth", true));
        response.addHeader(HttpHeaders.SET_COOKIE, build(CSRF_COOKIE, "", Duration.ZERO, "/", false));
    }

    public Optional<String> lerAccessToken(HttpServletRequest request) {
        return lerCookie(request, ACCESS_COOKIE);
    }

    public Optional<String> lerRefreshToken(HttpServletRequest request) {
        return lerCookie(request, REFRESH_COOKIE);
    }

    private Optional<String> lerCookie(HttpServletRequest request, String nome) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        for (var cookie : request.getCookies()) {
            if (nome.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    public String gerarCsrfToken() {
        var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String build(String nome, String valor, Duration maxAge, String path, boolean httpOnly) {
        var sb = new StringBuilder();
        sb.append(nome).append('=').append(valor == null ? "" : valor);
        sb.append("; Path=").append(path);
        sb.append("; Max-Age=").append(maxAge.getSeconds());
        if (httpOnly) {
            sb.append("; HttpOnly");
        }
        if (secure) {
            sb.append("; Secure");
        }
        // SameSite=Lax permite navegação de topo (link da proposta/galeria) sem
        // enviar cookies em requisições cross-site de terceiros.
        sb.append("; SameSite=Lax");
        return sb.toString();
    }
}
