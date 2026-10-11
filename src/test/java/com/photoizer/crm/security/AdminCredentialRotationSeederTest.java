package com.photoizer.crm.security;

import com.photoizer.crm.auth.model.Papel;
import com.photoizer.crm.auth.model.User;
import com.photoizer.crm.auth.repository.UserRepository;
import com.photoizer.crm.auth.seed.AdminCredentialRotationSeeder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Rotação forçada das credenciais semeadas pela V2 ({@code dev123}) fora de dev.
 */
@ExtendWith(MockitoExtension.class)
class AdminCredentialRotationSeederTest {

    private static final String ADMIN_EMAIL = "admin@photoizer.com";
    private static final String AGENDADOR_EMAIL = "agendamento@photoizer.com";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminCredentialRotationSeeder criarSeeder(String adminPassword,
                                                       String agendadorPassword,
                                                       boolean required) {
        return new AdminCredentialRotationSeeder(
            userRepository, passwordEncoder, "", adminPassword, agendadorPassword, required);
    }

    @Test
    @DisplayName("rotaciona a senha do admin quando ainda usa dev123")
    void rotacionaAdminComSenhaDeAmbiente() {
        var admin = new User(ADMIN_EMAIL, "hashDev123", "Administrador", Papel.ADMIN);
        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("dev123", "hashDev123")).thenReturn(true);
        when(passwordEncoder.encode("senha-forte-123456")).thenReturn("hashNovo");

        criarSeeder("senha-forte-123456", "", false).run();

        assertThat(admin.getPassword()).isEqualTo("hashNovo");
        verify(userRepository).save(admin);
    }

    @Test
    @DisplayName("falha o boot em prod: admin com dev123 e sem ADMIN_PASSWORD")
    void falhaQuandoAdminComDev123SemSenhaERequired() {
        var admin = new User(ADMIN_EMAIL, "hashDev123", "Administrador", Papel.ADMIN);
        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("dev123", "hashDev123")).thenReturn(true);

        var seeder = criarSeeder("", "", true);

        assertThatThrownBy(seeder::run).isInstanceOf(IllegalStateException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("não faz nada quando a senha já foi rotacionada")
    void naoFazNadaQuandoSenhaJaRotacionada() {
        var admin = new User(ADMIN_EMAIL, "hashRotacionado", "Administrador", Papel.ADMIN);
        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches(eq("dev123"), anyString())).thenReturn(false);

        criarSeeder("", "", true).run();

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("desativa o agendador com dev123 quando sem AGENDADOR_PASSWORD")
    void desativaAgendadorSemSenha() {
        var agendador = new User(AGENDADOR_EMAIL, "hashDev123", "Agendador", Papel.AGENDADOR);
        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(AGENDADOR_EMAIL)).thenReturn(Optional.of(agendador));
        when(passwordEncoder.matches("dev123", "hashDev123")).thenReturn(true);

        criarSeeder("", "", false).run();

        assertThat(agendador.isAtivo()).isFalse();
        verify(userRepository).save(agendador);
    }
}
