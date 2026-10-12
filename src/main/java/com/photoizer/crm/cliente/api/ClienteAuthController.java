package com.photoizer.crm.cliente.api;

import com.photoizer.crm.auth.config.AuthCookieService;
import com.photoizer.crm.cliente.api.dto.ClienteMapper;
import com.photoizer.crm.cliente.api.dto.ClienteResponse;
import com.photoizer.crm.cliente.service.ClienteAuthService;
import com.photoizer.crm.cliente.service.ClienteService;
import com.photoizer.crm.shared.config.OpenApiConfig;
import com.photoizer.crm.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
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
    private final AuthCookieService authCookieService;

    public ClienteAuthController(ClienteAuthService clienteAuthService, ClienteService clienteService,
                                 ClienteMapper clienteMapper, AuthCookieService authCookieService) {
        this.clienteAuthService = clienteAuthService;
        this.clienteService = clienteService;
        this.clienteMapper = clienteMapper;
        this.authCookieService = authCookieService;
    }

    /**
     * Registra novo cliente.
     * Padrão DTO Pattern - recebe DTO de request, retorna DTO de response.
     */
    @PostMapping("/registro")
    @Operation(operationId = "registrarCliente", summary = "Registrar novo cliente com email e senha")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Cliente registrado",
            content = @Content(schema = @Schema(implementation = ClienteAuthResponse.class))),
        @ApiResponse(responseCode = "409", description = "E-mail/telefone já cadastrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Dados inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ClienteAuthResponse> registrar(@Valid @RequestBody ClienteRegistroRequest request,
                                                         HttpServletResponse response) {
        var resultado = clienteAuthService.registrar(request);
        authCookieService.emitirCookies(response, resultado.token(), null);
        return ResponseEntity.status(HttpStatus.CREATED).body(resultado);
    }

    /**
     * Login de cliente.
     */
    @PostMapping("/login")
    @Operation(operationId = "loginCliente", summary = "Login de cliente")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Autenticado",
            content = @Content(schema = @Schema(implementation = ClienteAuthResponse.class))),
        @ApiResponse(responseCode = "401", description = "Credenciais inválidas",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ClienteAuthResponse> login(@Valid @RequestBody ClienteLoginRequest request,
                                                     HttpServletResponse response) {
        var resultado = clienteAuthService.login(request);
        authCookieService.emitirCookies(response, resultado.token(), null);
        return ResponseEntity.ok(resultado);
    }

    /**
     * Obtém perfil do cliente autenticado.
     * Retorna DTO de resposta (sem senhaHash).
     */
    @GetMapping("/perfil")
    @Operation(operationId = "perfilCliente", summary = "Obter perfil do cliente autenticado",
        security = {@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_BEARER),
            @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_COOKIE)})
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Perfil do cliente",
            content = @Content(schema = @Schema(implementation = ClienteResponse.class))),
        @ApiResponse(responseCode = "401", description = "Não autenticado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
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
    @Operation(operationId = "atualizarPerfilCliente", summary = "Atualizar perfil do cliente autenticado",
        security = {@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_BEARER),
            @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_COOKIE)})
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Perfil atualizado",
            content = @Content(schema = @Schema(implementation = ClienteResponse.class))),
        @ApiResponse(responseCode = "401", description = "Não autenticado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Dados inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ClienteResponse> atualizarPerfil(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody AtualizarPerfilRequest request) {
        var atualizado = clienteAuthService.atualizarPerfil(UUID.fromString(userId), request);
        return ResponseEntity.ok(clienteMapper.toResponse(atualizado));
    }
}
