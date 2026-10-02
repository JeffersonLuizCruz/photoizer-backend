package com.photoizer.crm.auth.seed;

import com.photoizer.crm.auth.model.Papel;
import com.photoizer.crm.auth.model.User;
import com.photoizer.crm.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bootstrap do primeiro ADMIN fora do perfil {@code dev} (achado C4).
 *
 * <p>Nunca semeia credenciais versionadas. As credenciais vêm de variáveis de
 * ambiente:
 * <ul>
 *   <li>{@code ADMIN_EMAIL} → {@code app.bootstrap.admin.email}</li>
 *   <li>{@code ADMIN_PASSWORD} → {@code app.bootstrap.admin.password}</li>
 * </ul>
 *
 * <p>Se a tabela {@code users} estiver vazia e as credenciais não forem
 * informadas, a subida falha ({@code IllegalStateException}) para evitar
 * ambiente sem administrador. Comportamento controlado por
 * {@code app.bootstrap.admin.required} (default {@code true}).
 */
@Component
@Profile("!dev")
@Order(1)
public class AdminBootstrapSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;
    private final boolean required;

    public AdminBootstrapSeeder(UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                @Value("${app.bootstrap.admin.email:}") String adminEmail,
                                @Value("${app.bootstrap.admin.password:}") String adminPassword,
                                @Value("${app.bootstrap.admin.required:true}") boolean required) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.required = required;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }

        boolean emailValido = adminEmail != null && !adminEmail.isBlank();
        boolean senhaValida = adminPassword != null && adminPassword.length() >= 12;

        if (!emailValido || !senhaValida) {
            var mensagem = "Nenhum usuário cadastrado e ADMIN_EMAIL/ADMIN_PASSWORD ausentes ou "
                + "inválidos (senha deve ter ao menos 12 caracteres).";
            if (required) {
                throw new IllegalStateException(mensagem
                    + " Defina as variáveis de ambiente ou desative app.bootstrap.admin.required.");
            }
            log.error(mensagem + " Boot prossegue sem administrador (app.bootstrap.admin.required=false).");
            return;
        }

        userRepository.save(new User(
            adminEmail.trim(),
            passwordEncoder.encode(adminPassword),
            "Administrador",
            Papel.ADMIN
        ));
        log.info("Usuário ADMIN de bootstrap criado a partir das variáveis de ambiente");
    }
}
