package com.photoizer.crm.security;

import com.photoizer.crm.auth.model.User;
import com.photoizer.crm.auth.repository.UserRepository;
import com.photoizer.crm.auth.seed.AdminBootstrapSeeder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C4: bootstrap de ADMIN fora de dev só a partir de variáveis de ambiente.
 */
@ExtendWith(MockitoExtension.class)
class AdminBootstrapSeederTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void criaAdminComCredenciaisDeAmbiente() {
        when(userRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode("senha-forte-123456")).thenReturn("hash");

        var seeder = new AdminBootstrapSeeder(
            userRepository, passwordEncoder, "admin@empresa.com", "senha-forte-123456", true);
        seeder.run();

        verify(userRepository).save(any(User.class));
    }

    @Test
    void falhaQuandoVazioSemCredenciaisERequired() {
        when(userRepository.count()).thenReturn(0L);

        var seeder = new AdminBootstrapSeeder(userRepository, passwordEncoder, "", "", true);

        assertThatThrownBy(seeder::run).isInstanceOf(IllegalStateException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void naoFazNadaQuandoJaExisteUsuario() {
        when(userRepository.count()).thenReturn(3L);

        var seeder = new AdminBootstrapSeeder(userRepository, passwordEncoder, "", "", true);
        seeder.run();

        verify(userRepository, never()).save(any());
    }

    @Test
    void senhaCurtaNaoEhAceita() {
        when(userRepository.count()).thenReturn(0L);

        var seeder = new AdminBootstrapSeeder(userRepository, passwordEncoder, "admin@x.com", "curta", true);

        assertThatThrownBy(seeder::run).isInstanceOf(IllegalStateException.class);
    }
}
