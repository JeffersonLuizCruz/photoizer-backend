package com.photoizer.crm.cliente.api;

import com.photoizer.crm.cliente.api.dto.ClienteMapper;
import com.photoizer.crm.cliente.api.dto.ClienteResponse;
import com.photoizer.crm.cliente.service.ClienteAuthService;
import com.photoizer.crm.cliente.service.ClienteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller para autenticação de clientes (e-commerce).
 * USA DTOs para request/response - nunca entidades JPA.
 * 
 * NOTA: Removida dependência com agenda (violação Modulith P1).
 * O endpoint de agendamentos do cliente deve ser movido para o módulo agenda.
 */
@RestController
@RequestMapping("/api/v1/auth/cliente")
@Tag(name = "Auth Cliente", description = "Cadastro e login de clientes do e-commerce")
public class ClienteAuthController {

    private final ClienteAuthService clienteAuthService;
    private final ClienteService clienteService;
    private final ClienteMapper clienteMapper;

    public ClienteAuthController(ClienteAuthService clienteAuthService, ClienteService clienteService,
                                 ClienteMapper clienteMapper) {
        this.clienteAuthService = clienteAuthService;
        this.clienteService = clienteService;
        this.clienteMapper = clienteMapper;
    }

    /**
     * Registra novo cliente.
     * Padrão DTO Pattern - recebe DTO de request, retorna DTO de response.
     */
    @PostMapping("/registro")
    @Operation(summary = "Registrar novo cliente com email e senha")
    public ResponseEntity<ClienteAuthResponse> registrar(@Valid @RequestBody ClienteRegistroRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteAuthService.registrar(request));
    }

    /**
     * Login de cliente.
     */
    @PostMapping("/login")
    @Operation(summary = "Login de cliente")
    public ResponseEntity<ClienteAuthResponse> login(@Valid @RequestBody ClienteLoginRequest request) {
        return ResponseEntity.ok(clienteAuthService.login(request));
    }

    /**
     * Obtém perfil do cliente autenticado.
     * Retorna DTO de resposta (sem senhaHash).
     */
    @GetMapping("/perfil")
    @Operation(summary = "Obter perfil do cliente autenticado")
    public ResponseEntity<ClienteResponse> perfil(@AuthenticationPrincipal String userId) {
        var cliente = clienteService.buscarPorId(UUID.fromString(userId));
        // Converte ClienteAdminResponse para ClienteResponse (mais leve)
        var response = new ClienteResponse(
            cliente.id(),
            cliente.nome(),
            cliente.telefone(),
            cliente.email(),
            cliente.cpf(),
            cliente.cidade(),
            cliente.estado(),
            cliente.origem(),
            cliente.observacoes(),
            cliente.dataCadastro(),
            cliente.preferencias()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Atualiza perfil do cliente autenticado.
     * Padrão DTO Pattern - recebe DTO de request, retorna DTO de response.
     */
    @PutMapping("/perfil")
    @Operation(summary = "Atualizar perfil do cliente autenticado")
    public ResponseEntity<ClienteResponse> atualizarPerfil(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody AtualizarPerfilRequest request) {
        var atualizado = clienteAuthService.atualizarPerfil(UUID.fromString(userId), request);
        return ResponseEntity.ok(clienteMapper.toResponse(atualizado));
    }
}
