package com.photoizer.crm.cliente.service;

import com.photoizer.crm.auth.config.JwtTokenProvider;
import com.photoizer.crm.cliente.api.AtualizarPerfilRequest;
import com.photoizer.crm.cliente.api.ClienteAuthResponse;
import com.photoizer.crm.cliente.api.ClienteLoginRequest;
import com.photoizer.crm.cliente.api.ClienteRegistroRequest;
import com.photoizer.crm.cliente.model.Cliente;
import com.photoizer.crm.cliente.model.OrigemCliente;
import com.photoizer.crm.cliente.repository.ClienteRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Autenticação de clientes do e-commerce (RF013).
 * Senhas criptografadas com BCrypt (RNF003).
 */
@Service
@Transactional
public class ClienteAuthService {

    private final ClienteRepository clienteRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public ClienteAuthService(ClienteRepository clienteRepository,
                              PasswordEncoder passwordEncoder,
                              JwtTokenProvider jwtTokenProvider) {
        this.clienteRepository = clienteRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public ClienteAuthResponse registrar(ClienteRegistroRequest request) {
        if (clienteRepository.findByEmailIgnoreCase(request.email()).isPresent()) {
            throw new IllegalArgumentException("Email já cadastrado");
        }
        if (clienteRepository.findByTelefone(request.telefone()).isPresent()) {
            throw new IllegalArgumentException("Telefone já cadastrado");
        }

        var cliente = Cliente.builder()
            .nome(request.nome())
            .email(request.email())
            .telefone(request.telefone())
            .senhaHash(passwordEncoder.encode(request.senha()))
            .dataCadastro(LocalDateTime.now())
            .preferencias(request.preferencias())
            .origem(OrigemCliente.OUTROS)
            .build();
        cliente = clienteRepository.save(cliente);

        var token = jwtTokenProvider.generateToken(cliente.getId(), cliente.getEmail());
        return new ClienteAuthResponse(token, cliente.getId(), cliente.getNome(),
            cliente.getEmail(), cliente.getTelefone());
    }

    public ClienteAuthResponse login(ClienteLoginRequest request) {
        var cliente = clienteRepository.findByEmailIgnoreCase(request.email())
            .orElseThrow(() -> new BadCredentialsException("Email ou senha inválidos"));

        if (cliente.getSenhaHash() == null
            || !passwordEncoder.matches(request.senha(), cliente.getSenhaHash())) {
            throw new BadCredentialsException("Email ou senha inválidos");
        }

        var token = jwtTokenProvider.generateToken(cliente.getId(), cliente.getEmail());
        return new ClienteAuthResponse(token, cliente.getId(), cliente.getNome(),
            cliente.getEmail(), cliente.getTelefone());
    }

    public Cliente atualizarPerfil(UUID clienteId, AtualizarPerfilRequest request) {
        var cliente = clienteRepository.findById(clienteId)
            .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado"));

        cliente.setNome(request.nome());
        cliente.setTelefone(request.telefone());
        cliente.setEmail(request.email());
        if (request.cpf() != null) cliente.setCpf(request.cpf());
        if (request.cidade() != null) cliente.setCidade(request.cidade());
        if (request.estado() != null) cliente.setEstado(request.estado());

        return clienteRepository.save(cliente);
    }
}
