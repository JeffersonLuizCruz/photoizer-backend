package com.photoizer.crm.shared.security;

import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Resolve o usuário autenticado para campos de auditoria.
 *
 * <p>Centraliza a leitura do {@link SecurityContextHolder} que antes estava
 * duplicada no {@code AuditInfoListener} e em serviços de domínio. Operações
 * sem request HTTP (schedulers, seeders, eventos) retornam {@code SYSTEM}.
 */
public final class CurrentUser {

    public static final String SYSTEM = "SYSTEM";

    private CurrentUser() {
    }

    public static String name() {
        try {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())
                && authentication.getName() != null) {
                return authentication.getName();
            }
        } catch (RuntimeException ignored) {
            // Contexto de segurança indisponível (testes, threads assíncronas).
        }
        return SYSTEM;
    }
}
