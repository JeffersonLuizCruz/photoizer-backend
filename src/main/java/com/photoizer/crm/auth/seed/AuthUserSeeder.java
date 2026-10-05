package com.photoizer.crm.auth.seed;

import com.photoizer.crm.auth.model.Papel;
import com.photoizer.crm.auth.model.User;
import com.photoizer.crm.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * PATTERN: Composite Seeder (CommandLineRunner + @Order)
 *
 * Seeder que completa o ambiente de desenvolvimento com os papéis de teste que
 * não vêm da migração Flyway (FOTOGRAFO e EDITOR). O ADMIN e o AGENDADOR oficiais
 * são semeados pela migração {@code V2__seed_usuarios_iniciais.sql} em todos os
 * ambientes (dev/homolog/prod).
 *
 * <p><b>C4 (segurança):</b> restrito ao perfil {@code dev}. Credenciais de teste
 * ({@code *@photoizer.com}/{@code dev123}) nunca existem fora de desenvolvimento.
 *
 * <p>Os e-mails usam o sufixo {@code .teste} de propósito para não colidirem
 * com a lista de limpeza de parceiros legados ({@link LegacySeedPartnerCleanupSeeder}),
 * que remove {@code carol/joao/maria/agendador@photoizer.com}.
 *
 * <p>Idempotência: cada usuário só é inserido se o e-mail ainda não existir —
 * o Flyway roda antes deste seeder e já cria ADMIN/AGENDADOR.
 * @Order(1) — deve rodar primeiro, pois outros módulos podem referenciar usuários.
 */
@Component
@Profile("dev")
@Order(1)
public class AuthUserSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AuthUserSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthUserSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        semearSeAusente("fotografo.teste@photoizer.com", "Fotógrafo Teste", Papel.FOTOGRAFO);
        semearSeAusente("editor.teste@photoizer.com", "Editor Teste", Papel.EDITOR);

        log.info("Usuários de desenvolvimento semeados (fotógrafo, editor)");
    }

    private void semearSeAusente(String email, String nome, Papel papel) {
        if (userRepository.existsByEmail(email)) {
            return;
        }
        userRepository.save(new User(email, passwordEncoder.encode("dev123"), nome, papel));
    }
}
