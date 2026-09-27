package com.photoizer.crm.auth.seed;

import com.photoizer.crm.auth.model.Papel;
import com.photoizer.crm.auth.model.User;
import com.photoizer.crm.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * PATTERN: Composite Seeder (CommandLineRunner + @Order)
 *
 * Seeder responsável por criar apenas o usuário ADMIN de bootstrap,
 * necessário para o primeiro login. Demais usuários (fotógrafos, editores,
 * agendadores) devem ser cadastrados pela tela de Parceiros/Usuários — não
 * são mais semeados no código.
 *
 * Idempotência: verifica userRepository.count() == 0 antes de inserir.
 * @Order(1) — deve rodar primeiro, pois outros módulos podem referenciar usuários.
 */
@Component
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
