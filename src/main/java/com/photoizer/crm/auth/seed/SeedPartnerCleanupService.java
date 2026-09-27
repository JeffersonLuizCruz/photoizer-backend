package com.photoizer.crm.auth.seed;

import com.photoizer.crm.auth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Operações de limpeza dos parceiros semeados legados.
 *
 * Cada método roda em transação própria (REQUIRES_NEW) para que uma violação
 * de FK (usuário vinculado a agendamento/edição/contrato) não marque a
 * transação do chamador como rollback-only, permitindo o fallback de desativação.
 */
@Service
public class SeedPartnerCleanupService {

    private final UserRepository userRepository;

    public SeedPartnerCleanupService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void excluir(UUID userId) {
        userRepository.findById(userId).ifPresent(user -> {
            userRepository.delete(user);
            userRepository.flush();
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void desativar(UUID userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setAtivo(false);
            userRepository.save(user);
        });
    }
}
