package com.photoizer.crm.auth.api;

import com.photoizer.crm.auth.config.AuthCookieService;
import com.photoizer.crm.auth.service.AuthService;
import com.photoizer.crm.auth.service.RefreshTokenService;
import com.photoizer.crm.auth.service.UserService;
import com.photoizer.crm.shared.config.OpenApiConfig;
import com.photoizer.crm.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticação", description = "Login, refresh token, logout e dados do usuário autenticado (staff).")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final AuthCookieService authCookieService;
    private final UserService userService;

    public AuthController(AuthService authService,
                          RefreshTokenService refreshTokenService,
                          AuthCookieService authCookieService,
                          UserService userService) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.authCookieService = authCookieService;
        this.userService = userService;
    }

    @PostMapping("/login")
    @Operation(operationId = "login",
        summary = "Autenticar administrador/equipe",
        description = "Valida as credenciais e emite cookies HttpOnly (access/refresh) + cookie CSRF. "
            + "Os tokens também retornam no corpo para integrações via Authorization: Bearer.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Autenticado com sucesso",
            content = @Content(schema = @Schema(implementation = LoginResponse.class))),
        @ApiResponse(responseCode = "401", description = "Credenciais inválidas",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Dados de entrada inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletResponse response) {
        var resultado = authService.login(request);
        // A3: entrega tokens em cookies HttpOnly (access/refresh) + CSRF legível.
        authCookieService.emitirCookies(response, resultado.token(), resultado.refreshToken());
        return ResponseEntity.ok(resultado);
    }

    @PostMapping("/refresh")
    @Operation(operationId = "refreshToken",
        summary = "Renovar access token usando refresh token",
        description = "Aceita o refresh token via corpo ou cookie HttpOnly. Há rotação do refresh "
            + "a cada chamada; reuso do token anterior revoga a família.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Token renovado",
            content = @Content(schema = @Schema(implementation = RefreshTokenResponse.class))),
        @ApiResponse(responseCode = "401", description = "Refresh token inválido, expirado ou reutilizado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Map<String, String>> refresh(@RequestBody(required = false) RefreshTokenRequest request,
                                                       HttpServletRequest servletRequest,
                                                       HttpServletResponse response) {
        var refreshToken = resolverRefreshToken(request, servletRequest);
        var resultado = refreshTokenService.refreshAccessToken(refreshToken);
        // Reemite os cookies com o par rotacionado (access + refresh).
        authCookieService.emitirCookies(response, resultado.accessToken(), resultado.refreshToken());
        return ResponseEntity.ok(Map.of("accessToken", resultado.accessToken()));
    }

    @PostMapping("/logout")
    @Operation(operationId = "logout",
        summary = "Revogar tokens (access + refresh)",
        security = {@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_BEARER),
            @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_COOKIE)})
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Sessão encerrada"),
        @ApiResponse(responseCode = "401", description = "Não autenticado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshTokenRequest request,
                                       Authentication authentication,
                                       HttpServletRequest servletRequest,
                                       HttpServletResponse response) {
        if (authentication != null && authentication.getCredentials() != null) {
            refreshTokenService.blockAccessToken(authentication.getCredentials().toString());
        } else {
            authCookieService.lerAccessToken(servletRequest)
                .ifPresent(refreshTokenService::blockAccessToken);
        }
        var refreshToken = resolverRefreshToken(request, servletRequest);
        if (refreshToken != null) {
            refreshTokenService.revokeRefreshToken(refreshToken);
        }
        authCookieService.limparCookies(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(operationId = "getCurrentUser",
        summary = "Dados do usuário autenticado (derivados do servidor)",
        security = {@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_BEARER),
            @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_COOKIE)})
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usuário autenticado",
            content = @Content(schema = @Schema(implementation = MeResponse.class))),
        @ApiResponse(responseCode = "401", description = "Não autenticado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<MeResponse> me(@AuthenticationPrincipal String userId) {
        var user = userService.buscarPorId(UUID.fromString(userId));
        return ResponseEntity.ok(new MeResponse(user.nome(), user.email(), user.papel(), user.id()));
    }

    private String resolverRefreshToken(RefreshTokenRequest request, HttpServletRequest servletRequest) {
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            return request.refreshToken();
        }
        return authCookieService.lerRefreshToken(servletRequest).orElse(null);
    }

    @Schema(name = "RefreshTokenRequest", description = "Refresh token (opcional quando enviado por cookie).")
    public record RefreshTokenRequest(
        @Schema(description = "Refresh token previamente emitido") String refreshToken) {}

    @Schema(name = "RefreshTokenResponse", description = "Novo access token.")
    public record RefreshTokenResponse(
        @Schema(description = "Access token (JWT)") String accessToken) {}
}
