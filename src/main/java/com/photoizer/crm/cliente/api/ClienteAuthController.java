package com.photoizer.crm.cliente.api;

import com.photoizer.crm.agenda.service.AgendamentoService;
import com.photoizer.crm.cliente.model.Cliente;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth/cliente")
@Tag(name = "Auth Cliente", description = "Cadastro e login de clientes do e-commerce")
public class ClienteAuthController {

    private final ClienteAuthService clienteAuthService;
    private final ClienteService clienteService;
    private final AgendamentoService agendamentoService;

    public ClienteAuthController(ClienteAuthService clienteAuthService, ClienteService clienteService,
                                 AgendamentoService agendamentoService) {
        this.clienteAuthService = clienteAuthService;
        this.clienteService = clienteService;
        this.agendamentoService = agendamentoService;
    }

    @PostMapping("/registro")
    @Operation(summary = "Registrar novo cliente com email e senha")
    public ResponseEntity<ClienteAuthResponse> registrar(@Valid @RequestBody ClienteRegistroRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteAuthService.registrar(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Login de cliente")
    public ResponseEntity<ClienteAuthResponse> login(@Valid @RequestBody ClienteLoginRequest request) {
        return ResponseEntity.ok(clienteAuthService.login(request));
    }

    @GetMapping("/perfil")
    @Operation(summary = "Obter perfil do cliente autenticado")
    public ResponseEntity<Cliente> perfil(@AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(clienteService.buscarPorId(java.util.UUID.fromString(userId)));
    }

    @PutMapping("/perfil")
    @Operation(summary = "Atualizar perfil do cliente autenticado")
    public ResponseEntity<Cliente> atualizarPerfil(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody AtualizarPerfilRequest request) {
        return ResponseEntity.ok(clienteAuthService.atualizarPerfil(java.util.UUID.fromString(userId), request));
    }

    @GetMapping("/agendamentos")
    @Operation(summary = "Listar agendamentos do cliente autenticado com status das fotos")
    public ResponseEntity<List<AgendamentoClienteResponse>> listarAgendamentos(
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(
            agendamentoService.listarAgendamentosCliente(UUID.fromString(userId)));
    }
}
