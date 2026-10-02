package com.photoizer.crm.security;

import com.photoizer.crm.auth.config.AuthCookieService;
import com.photoizer.crm.auth.config.CsrfCookieFilter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes do double-submit CSRF (achado A3).
 */
class CsrfCookieFilterTest {

    private final CsrfCookieFilter filter = new CsrfCookieFilter();

    @Test
    void postAutenticadoPorCookieSemHeaderEhBloqueado() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/clientes");
        request.setCookies(new Cookie(AuthCookieService.ACCESS_COOKIE, "jwt"));
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void postAutenticadoPorCookieComHeaderCorretoPassa() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/clientes");
        request.setCookies(
            new Cookie(AuthCookieService.ACCESS_COOKIE, "jwt"),
            new Cookie(AuthCookieService.CSRF_COOKIE, "abc123"));
        request.addHeader(AuthCookieService.CSRF_HEADER, "abc123");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isNotEqualTo(403);
    }

    @Test
    void headerDivergenteDoCookieEhBloqueado() throws Exception {
        var request = new MockHttpServletRequest("DELETE", "/api/v1/clientes/1");
        request.setCookies(
            new Cookie(AuthCookieService.ACCESS_COOKIE, "jwt"),
            new Cookie(AuthCookieService.CSRF_COOKIE, "abc123"));
        request.addHeader(AuthCookieService.CSRF_HEADER, "outro-valor");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void getNaoEhAfetado() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/clientes");
        request.setCookies(new Cookie(AuthCookieService.ACCESS_COOKIE, "jwt"));
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isNotEqualTo(403);
    }

    @Test
    void bearerNaoEhSujeitoACsrf() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/clientes");
        request.addHeader("Authorization", "Bearer algum-token");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isNotEqualTo(403);
    }

    @Test
    void requisicaoPublicaSemCookiePassa() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/ecommerce/galeria/abc/checkout");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isNotEqualTo(403);
    }
}
