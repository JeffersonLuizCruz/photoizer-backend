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

import java.util.List;

/**
 * PATTERN: Composite Seeder (CommandLineRunner + @Order)
 *
 * Seeder responsável por criar os usuários iniciais do sistema.
 * Cada módulo é dono dos seus dados semeados — responsabilidade única.
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
            userRepository.saveAll(List.of(
                new User("admin@photoizer.com", passwordEncoder.encode("dev123"), "Administrador", Papel.ADMIN),
                new User("carol@photoizer.com", passwordEncoder.encode("dev123"), "Carol (Fotógrafa)", Papel.FOTOGRAFO),
                new User("joao@photoizer.com", passwordEncoder.encode("dev123"), "João (Editor)", Papel.EDITOR),
                new User("maria@photoizer.com", passwordEncoder.encode("dev123"), "Maria (Assistente)", Papel.EDITOR),
                new User("agendador@photoizer.com", passwordEncoder.encode("dev123"), "Lucas (Agendador)", Papel.AGENDADOR)
            ));
            log.info("Usuários semeados: 5");
        }
    }
}
