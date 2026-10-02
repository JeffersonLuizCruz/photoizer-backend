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
 * Seeder responsável por criar apenas o usuário ADMIN de bootstrap em
 * desenvolvimento, necessário para o primeiro login. Demais usuários
 * (fotógrafos, editores, agendadores) devem ser cadastrados pela tela de
 * Parceiros/Usuários — não são mais semeados no código.
 *
 * <p><b>C4 (segurança):</b> restrito ao perfil {@code dev}. Credenciais
 * hardcoded ({@code admin@photoizer.com}/{@code dev123}) nunca devem existir
 * fora de desenvolvimento. Em homolog/prod o bootstrap é feito por
 * {@link AdminBootstrapSeeder} a partir de variáveis de ambiente.
 *
 * Idempotência: verifica userRepository.count() == 0 antes de inserir.
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
        if (userRepository.count() == 0) {
            userRepository.save(
                new User("admin@photoizer.com", passwordEncoder.encode("dev123"), "Administrador", Papel.ADMIN)
            );
            log.info("Usuário ADMIN de bootstrap semeado");
        }
    }
}
