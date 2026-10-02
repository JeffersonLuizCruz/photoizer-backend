package com.photoizer.crm.security;

import com.photoizer.crm.cliente.api.ClienteRegistroRequest;
import com.photoizer.crm.cliente.api.dto.ClienteMapper;
import com.photoizer.crm.cliente.exception.ClienteDuplicadoException;
import com.photoizer.crm.cliente.model.Cliente;
import com.photoizer.crm.cliente.repository.ClienteRepository;
import com.photoizer.crm.cliente.service.ClienteAuthService;
import com.photoizer.crm.shared.auth.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * H3: o cadastro duplicado não deve revelar qual campo (e-mail/telefone)
 * já existe, dificultando a enumeração de contas.
 */
@ExtendWith(MockitoExtension.class)
class ClienteAuthServiceTest {

    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenService tokenService;
    @Mock
    private ClienteMapper clienteMapper;

    private ClienteAuthService service;

    @BeforeEach
    void setUp() {
        service = new ClienteAuthService(clienteRepository, passwordEncoder, tokenService, clienteMapper);
    }

    @Test
    @DisplayName("E-mail duplicado retorna mensagem genérica sem PII")
    void emailDuplicadoMensagemGenerica() {
        when(clienteRepository.findByEmailIgnoreCase("ana@x.com"))
            .thenReturn(Optional.of(Cliente.builder().build()));

        var request = new ClienteRegistroRequest("Ana", "ana@x.com", "11999999999", "senha123", null);

        assertThatThrownBy(() -> service.registrar(request))
            .isInstanceOf(ClienteDuplicadoException.class)
            .hasMessageContaining("Não foi possível concluir o cadastro")
            .hasMessageNotContaining("email")
            .hasMessageNotContaining("ana@x.com");
    }

    @Test
    @DisplayName("Telefone duplicado retorna a mesma mensagem genérica")
    void telefoneDuplicadoMensagemGenerica() {
        when(clienteRepository.findByEmailIgnoreCase("novo@x.com")).thenReturn(Optional.empty());
        when(clienteRepository.findByTelefone("11999999999"))
            .thenReturn(Optional.of(Cliente.builder().build()));

        var request = new ClienteRegistroRequest("Novo", "novo@x.com", "11999999999", "senha123", null);

        assertThatThrownBy(() -> service.registrar(request))
            .isInstanceOf(ClienteDuplicadoException.class)
            .hasMessageContaining("Não foi possível concluir o cadastro")
            .hasMessageNotContaining("11999999999");
    }
}
