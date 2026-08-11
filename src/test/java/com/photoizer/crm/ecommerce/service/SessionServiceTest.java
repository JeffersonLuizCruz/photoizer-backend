package com.photoizer.crm.ecommerce.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionServiceTest {

    private SessionService service;

    @BeforeEach
    void setUp() {
        service = new SessionService("photoizer-crm-secret-key-change-in-production-minimum-256-bits-long-for-hs256");
    }

    @Test
    void sessaoEmitidaEhValida() {
        var sessao = service.emitir();
        assertTrue(service.valida(sessao));
    }

    @Test
    void sessoesSaoDistintas() {
        assertNotEquals(service.emitir(), service.emitir());
    }

    @Test
    void sessaoForjadaEhRejeitada() {
        var sessao = service.emitir();
        var forjada = sessao.substring(0, sessao.indexOf('.')) + ".0000000000000000000000000000000000000000000000000000000000000000";
        assertFalse(service.valida(forjada));
    }

    @Test
    void sessaoComAssinaturaTrocadaEhRejeitada() {
        var a = service.emitir();
        var b = service.emitir();
        var aUuid = a.substring(0, a.indexOf('.'));
        var bUuid = b.substring(0, b.indexOf('.'));
        assertFalse(service.valida(aUuid + "." + b.substring(b.indexOf('.') + 1)));
        assertFalse(service.valida(bUuid + "." + a.substring(a.indexOf('.') + 1)));
    }

    @Test
    void formatoInvalidoEhRejeitado() {
        assertFalse(service.valida(null));
        assertFalse(service.valida(""));
        assertFalse(service.valida("nao-e-uuid.assinatura"));
        assertFalse(service.valida("00000000-0000-0000-0000-000000000000.assinatura"));
        assertFalse(service.valida("somente-uuid"));
    }

    @Test
    void sessaoDeOutroSegredoEhRejeitada() {
        var outroServico = new SessionService("outro-segredo-diferente-do-original-para-o-teste-hs256-minimo");
        var sessao = service.emitir();
        assertFalse(outroServico.valida(sessao));
    }
}
