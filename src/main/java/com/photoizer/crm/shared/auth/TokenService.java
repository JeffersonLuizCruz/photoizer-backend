package com.photoizer.crm.shared.auth;

import java.util.UUID;

/**
 * Interface para serviços de geração de tokens.
 * Padrão Dependency Inversion - módulos dependem de abstração, não de implementação.
 * 
 * Permite que o módulo cliente gere tokens JWT sem depender diretamente do módulo auth.
 * A implementação fica no módulo auth (JwtTokenProvider).
 */
public interface TokenService {

    /**
     * Gera token JWT para o usuário.
     * 
     * @param userId ID do usuário
     * @param email Email do usuário
     * @param papel Papel do usuário (ex: CLIENTE, ADMIN)
     * @return Token JWT gerado
     */
    String generateToken(UUID userId, String email, String papel);
}
