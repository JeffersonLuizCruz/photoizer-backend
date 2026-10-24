package com.photoizer.crm.auth.seed;

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

import java.util.LinkedHashSet;

/**
 * Rotação forçada das credenciais semeadas pela migração
 * {@code V2__seed_usuarios_iniciais.sql} fora do perfil {@code dev}.
 *
 * <p>A migração V2 cria {@code admin@photoizer.com} e
 * {@code agendamento@photoizer.com} com a senha padrão {@code dev123} em
 * <b>todos</b> os ambientes. Como o {@link AdminBootstrapSeeder} só age quando a
 * tabela {@code users} está vazia (nunca fica, por causa da própria V2), um
 * ambiente de homologação/produção subiria com a senha padrão. Este seeder
 * elimina esse risco no boot:
 *
 * <ul>
 *   <li><b>ADMIN</b> ({@code ADMIN_EMAIL}, default {@code admin@photoizer.com}):
 *       se ainda usar {@code dev123}, sobrescreve com {@code ADMIN_PASSWORD};
 *       sem a variável, falha a subida quando
 *       {@code app.bootstrap.admin.required=true} (PROD) e apenas loga erro caso
 *       contrário (HOMOLOG).</li>
 *   <li><b>AGENDADOR</b> ({@code agendamento@photoizer.com}): se ainda usar
 *       {@code dev123}, sobrescreve com {@code AGENDADOR_PASSWORD}; sem a
 *       variável, <b>desativa</b> a conta (nunca permanece com senha padrão).</li>
 * </ul>
 *
 * <p>Idempotente: atua apenas enquanto a senha for a padrão de desenvolvimento.
 * Após a primeira rotação, as variáveis podem ser removidas do orquestrador.
 *
 * <p>{@code @Order(0)} — roda antes do {@link AdminBootstrapSeeder} e dos demais.
 */
@Component
@Profile("!dev")
@Order(0)
public class AdminCredentialRotationSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminCredentialRotationSeeder.class);

    private static final String DEFAULT_ADMIN_EMAIL = "admin@photoizer.com";
    private static final String DEFAULT_AGENDADOR_EMAIL = "agendamento@photoizer.com";
    private static final String SEED_PASSWORD = "dev123";
    private static final int MIN_PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;
    private final String agendadorPassword;
    private final boolean required;

    public AdminCredentialRotationSeeder(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap.admin.email:}") String adminEmail,
            @Value("${app.bootstrap.admin.password:}") String adminPassword,
            @Value("${app.bootstrap.agendador.password:}") String agendadorPassword,
            @Value("${app.bootstrap.admin.required:false}") boolean required) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.agendadorPassword = agendadorPassword;
        this.required = required;
    }

    @Override
    @Transactional
    public void run(String... args) {
        rotacionarAdmins();
        rotacionarAgendador();
    }

    private void rotacionarAdmins() {
        var alvos = new LinkedHashSet<String>();
        alvos.add(DEFAULT_ADMIN_EMAIL);
        if (adminEmail != null && !adminEmail.isBlank()) {
            alvos.add(adminEmail.trim());
        }

        for (var email : alvos) {
            var userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                continue;
            }
            var user = userOpt.get();
            if (!passwordEncoder.matches(SEED_PASSWORD, user.getPassword())) {
                continue;
            }

            if (senhaValida(adminPassword)) {
                rotacionar(user, adminPassword);
                log.info("Senha do ADMIN padrão rotacionada a partir de ADMIN_PASSWORD");
            } else if (required) {
                throw new IllegalStateException(mensagemAdminPadrao());
            } else {
                log.error("{} Boot prossegue (app.bootstrap.admin.required=false).", mensagemAdminPadrao());
            }
        }
    }

    private void rotacionarAgendador() {
        var userOpt = userRepository.findByEmail(DEFAULT_AGENDADOR_EMAIL);
        if (userOpt.isEmpty()) {
            return;
        }
        var user = userOpt.get();
        if (!passwordEncoder.matches(SEED_PASSWORD, user.getPassword())) {
            return;
        }

        if (senhaValida(agendadorPassword)) {
            rotacionar(user, agendadorPassword);
            log.info("Senha do AGENDADOR padrão rotacionada a partir de AGENDADOR_PASSWORD");
        } else {
            user.setAtivo(false);
            userRepository.save(user);
            log.warn("AGENDADOR ainda usava a senha padrão de desenvolvimento e "
                + "AGENDADOR_PASSWORD não foi definido; conta DESATIVADA.");
        }
    }

    private void rotacionar(User user, String novaSenha) {
        user.setPassword(passwordEncoder.encode(novaSenha));
        userRepository.save(user);
    }

    private boolean senhaValida(String senha) {
        return senha != null && senha.length() >= MIN_PASSWORD_LENGTH;
    }

    private String mensagemAdminPadrao() {
        return "O usuário ADMIN padrão ainda usa a senha de desenvolvimento "
            + "e ADMIN_PASSWORD não foi definido (mínimo " + MIN_PASSWORD_LENGTH
            + " caracteres). Defina ADMIN_PASSWORD para rotacionar antes de subir em homolog/prod.";
    }
}
