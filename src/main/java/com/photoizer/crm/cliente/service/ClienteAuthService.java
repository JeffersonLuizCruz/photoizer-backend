package com.photoizer.crm.cliente.service;

import com.photoizer.crm.cliente.api.AtualizarPerfilRequest;
import com.photoizer.crm.cliente.api.ClienteAuthResponse;
import com.photoizer.crm.cliente.api.ClienteLoginRequest;
import com.photoizer.crm.cliente.api.ClienteRegistroRequest;
import com.photoizer.crm.cliente.api.dto.ClienteMapper;
import com.photoizer.crm.cliente.exception.ClienteDuplicadoException;
import com.photoizer.crm.cliente.exception.ClienteNaoEncontradoException;
import com.photoizer.crm.cliente.model.Cliente;
import com.photoizer.crm.cliente.model.OrigemCliente;
import com.photoizer.crm.cliente.repository.ClienteRepository;
import com.photoizer.crm.shared.auth.TokenService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Autenticação de clientes do e-commerce (RF013).
 * Senhas criptografadas com BCrypt (RNF003).
 * 
 * NOTA: Usa TokenService (abstração) em vez de JwtTokenProvider (implementação).
 * Padrão Dependency Inversion - módulo cliente depende de abstração do shared.
 */
@Service
@Transactional
public class ClienteAuthService {

    private final ClienteRepository clienteRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final ClienteMapper clienteMapper;

    public ClienteAuthService(ClienteRepository clienteRepository,
                              PasswordEncoder passwordEncoder,
                              TokenService tokenService,
                              ClienteMapper clienteMapper) {
        this.clienteRepository = clienteRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.clienteMapper = clienteMapper;
    }

    /**
     * Registra novo cliente.
     * Padrão DTO Pattern - recebe DTO de request, retorna DTO de response.
     */
    public ClienteAuthResponse registrar(ClienteRegistroRequest request) {
        if (clienteRepository.findByEmailIgnoreCase(request.email()).isPresent()) {
            throw new ClienteDuplicadoException("email", request.email());
        }
        if (clienteRepository.findByTelefone(request.telefone()).isPresent()) {
            throw new ClienteDuplicadoException("telefone", request.telefone());
        }

        var cliente = Cliente.builder()
            .nome(request.nome())
            .email(request.email())
            .telefone(request.telefone())
            .senhaHash(passwordEncoder.encode(request.senha()))
            .preferencias(request.preferencias())
            .origem(OrigemCliente.OUTROS)
            .build();
        cliente = clienteRepository.save(cliente);

        var token = tokenService.generateToken(cliente.getId(), cliente.getEmail(), "CLIENTE");
        return new ClienteAuthResponse(token, cliente.getId(), cliente.getNome(),
            cliente.getEmail(), cliente.getTelefone());
    }

    /**
     * Login de cliente.
     */
    public ClienteAuthResponse login(ClienteLoginRequest request) {
        var cliente = clienteRepository.findByEmailIgnoreCase(request.email())
            .orElseThrow(() -> new BadCredentialsException("Email ou senha inválidos"));

        if (cliente.getSenhaHash() == null
            || !passwordEncoder.matches(request.senha(), cliente.getSenhaHash())) {
            throw new BadCredentialsException("Email ou senha inválidos");
        }

        var token = tokenService.generateToken(cliente.getId(), cliente.getEmail(), "CLIENTE");
        return new ClienteAuthResponse(token, cliente.getId(), cliente.getNome(),
            cliente.getEmail(), cliente.getTelefone());
    }

    /**
     * Atualiza perfil do cliente.
     * Retorna entidade para uso interno (será convertida pelo controller).
     */
    public Cliente atualizarPerfil(UUID clienteId, AtualizarPerfilRequest request) {
        var cliente = clienteRepository.findById(clienteId)
            .orElseThrow(() -> new ClienteNaoEncontradoException(clienteId));

        clienteMapper.updatePerfil(cliente, request);
        return clienteRepository.save(cliente);
    }
}
