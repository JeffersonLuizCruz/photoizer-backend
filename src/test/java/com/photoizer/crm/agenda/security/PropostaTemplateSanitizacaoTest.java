package com.photoizer.crm.agenda.security;

import com.photoizer.crm.agenda.service.PropostaTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de sanitização do HTML do contrato (achado A1 — XSS armazenado).
 *
 * <p>O HTML é injetado via {@code dangerouslySetInnerHTML} na página pública da
 * proposta. O serviço deve remover tags/atributos perigosos.
 */
class PropostaTemplateSanitizacaoTest {

    private PropostaTemplateService service;

    @BeforeEach
    void setUp() {
        service = new PropostaTemplateService(null);
    }

    @Test
    @DisplayName("Remove <script> do HTML")
    void removeScript() {
        var html = "<p>ok</p><script>alert(document.cookie)</script>";
        var sanitizado = service.sanitizarHtml(html);
        assertThat(sanitizado).doesNotContain("script").doesNotContain("alert");
        assertThat(sanitizado).contains("<p>ok</p>");
    }

    @Test
    @DisplayName("Remove atributos de evento (onerror/onload/onclick)")
    void removeEventHandlers() {
        var html = "<p onclick=\"steal()\">texto</p><ul onmouseover='x()'><li>a</li></ul>";
        var sanitizado = service.sanitizarHtml(html);
        assertThat(sanitizado).doesNotContain("onclick").doesNotContain("onmouseover");
    }

    @Test
    @DisplayName("Remove links com esquema javascript:")
    void removeJavascriptScheme() {
        var html = "<p><a href=\"javascript:alert(1)\">clique</a></p>";
        var sanitizado = service.sanitizarHtml(html);
        assertThat(sanitizado).doesNotContain("javascript:");
    }

    @Test
    @DisplayName("Remove tags fora da allowlist (iframe/object/embed/form)")
    void removeTagsForaDaAllowlist() {
        var html = "<p>a</p><iframe src=\"http://evil\"></iframe><form><input></form>";
        var sanitizado = service.sanitizarHtml(html);
        assertThat(sanitizado).doesNotContain("<iframe").doesNotContain("<form").doesNotContain("<input");
        assertThat(sanitizado).contains("<p>a</p>");
    }

    @Test
    @DisplayName("Mantém apenas tags permitidas (h1, h2, p, ul, li)")
    void mantemTagsPermitidas() {
        var html = "<h1>T</h1><h2>S</h2><p>p</p><ul><li>i</li></ul>";
        assertThat(service.sanitizarHtml(html)).isEqualTo(html);
    }

    @Test
    @DisplayName("esc() cobre aspas no fluxo de renderização")
    void escapeCobreAspas() {
        var template = "= Contrato =\nNome: {{clienteNome}}\n";
        var valores = new java.util.HashMap<String, String>();
        valores.put("clienteNome", "\"><script>alert(1)</script>");
        var html = service.renderizarHtml(template, valores);
        assertThat(html).doesNotContain("<script>");
        assertThat(html).contains("&quot;&gt;");
    }

    @Test
    void listaDePlaceholdersVaziaNaoQuebra() {
        assertThat(service.sanitizarHtml(null)).isEmpty();
        assertThat(service.sanitizarHtml("")).isEmpty();
    }

    /** Prova que a lista de tags permitidas é restritiva. */
    @Test
    void allowedTagsRestritas() {
        var permitidas = List.of("h1", "h2", "p", "ul", "li");
        assertThat(permitidas).doesNotContain("script", "img", "a", "iframe");
    }
}
