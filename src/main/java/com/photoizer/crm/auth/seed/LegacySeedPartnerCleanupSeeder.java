package com.photoizer.crm.auth.seed;

import com.photoizer.crm.auth.event.ParceiroSementeDesativadoEvent;
import com.photoizer.crm.auth.model.User;
import com.photoizer.crm.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * PATTERN: Composite Seeder (CommandLineRunner + @Order)
 *
 * Remove parceiros que eram semeados no código por versões antigas
 * (Carol/João/Maria/Lucas) e que passaram a poluir a seleção de parceiros.
 *
 * Idempotente: usuários já removidos não são encontrados; desativados são ignorados.
 * Se o usuário estiver vinculado (violação de FK), é apenas desativado e um
 * evento é publicado para o módulo agenda cancelar repasses pendentes.
 *
 * @Order(2) — roda logo após o AuthUserSeeder.
 */
@Component
@Order(2)
public class LegacySeedPartnerCleanupSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacySeedPartnerCleanupSeeder.class);

    private static final List<String> LEGACY_SEED_EMAILS = List.of(
        "carol@photoizer.com",
        "joao@photoizer.com",
        "maria@photoizer.com",
        "agendador@photoizer.com"
    );

    private final UserRepository userRepository;
    private final SeedPartnerCleanupService cleanupService;
    private final ApplicationEventPublisher eventPublisher;

    public LegacySeedPartnerCleanupSeeder(UserRepository userRepository,
                                          SeedPartnerCleanupService cleanupService,
                                          ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.cleanupService = cleanupService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void run(String... args) {
        for (var email : LEGACY_SEED_EMAILS) {
            userRepository.findByEmail(email)
                .filter(User::isAtivo)
                .ifPresent(user -> cleanup(user.getId(), email));
        }
    }

    private void cleanup(UUID userId, String email) {
        try {
            cleanupService.excluir(userId);
            log.info("Parceiro semeado legado removido: {}", email);
        } catch (DataIntegrityViolationException e) {
            cleanupService.desativar(userId);
            eventPublisher.publishEvent(new ParceiroSementeDesativadoEvent(userId));
            log.warn("Parceiro semeado legado possui vínculos e foi desativado: {}", email);
        } catch (RuntimeException e) {
            log.error("Falha ao limpar parceiro semeado legado {}: {}", email, e.getMessage());
        }
    }
}
