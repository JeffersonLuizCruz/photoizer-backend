package com.photoizer.crm.security;

import com.photoizer.crm.auth.config.AuthCookieService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes do gerenciamento de cookies de autenticação (achado A3).
 */
class AuthCookieServiceTest {

    private AuthCookieService novoService(boolean secure) {
        return new AuthCookieService(secure, 86_400_000L, 604_800_000L);
    }

    @Test
    void emiteCookieAccessHttpOnlyESameSite() {
        var response = new MockHttpServletResponse();
        novoService(false).emitirCookies(response, "access-jwt", "refresh-jwt");

        var setCookies = response.getHeaders("Set-Cookie");
        assertThat(setCookies).hasSize(3);

        var access = setCookies.stream()
            .filter(c -> c.startsWith(AuthCookieService.ACCESS_COOKIE + "="))
            .findFirst().orElseThrow();
        assertThat(access).contains("HttpOnly").contains("SameSite=Lax").contains("access-jwt");
        assertThat(access).doesNotContain("Secure");

        var csrf = setCookies.stream()
            .filter(c -> c.startsWith(AuthCookieService.CSRF_COOKIE + "="))
            .findFirst().orElseThrow();
        // CSRF precisa ser legível por JS (sem HttpOnly).
        assertThat(csrf).doesNotContain("HttpOnly");
    }

    @Test
    void secureFlagAplicadoQuandoConfigurado() {
        var response = new MockHttpServletResponse();
        novoService(true).emitirCookies(response, "a", "b");
        assertThat(response.getHeaders("Set-Cookie")).allMatch(c -> c.contains("Secure"));
    }

    @Test
    void leAccessTokenDoCookie() {
        var request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthCookieService.ACCESS_COOKIE, "meu-token"));
        assertThat(novoService(false).lerAccessToken(request)).contains("meu-token");
    }

    @Test
    void limparCookiesZeraMaxAge() {
        var response = new MockHttpServletResponse();
        novoService(false).limparCookies(response);
        assertThat(response.getHeaders("Set-Cookie")).allMatch(c -> c.contains("Max-Age=0"));
    }

    @Test
    void csrfTokenEhAleatorioEUrlSafe() {
        var service = novoService(false);
        var a = service.gerarCsrfToken();
        var b = service.gerarCsrfToken();
        assertThat(a).isNotEqualTo(b).hasSizeGreaterThan(20);
    }
}
