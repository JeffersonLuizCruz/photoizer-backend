package com.photoizer.crm.auth.api;

import com.photoizer.crm.auth.config.AuthCookieService;
import com.photoizer.crm.auth.service.AuthService;
import com.photoizer.crm.auth.service.RefreshTokenService;
import com.photoizer.crm.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "Autenticação", description = "Login, refresh token e logout")
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
    @Operation(summary = "Autenticar administrador")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletResponse response) {
        var resultado = authService.login(request);
        // A3: entrega tokens em cookies HttpOnly (access/refresh) + CSRF legível.
        authCookieService.emitirCookies(response, resultado.token(), resultado.refreshToken());
        return ResponseEntity.ok(resultado);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar access token usando refresh token")
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
    @Operation(summary = "Revogar tokens (access + refresh)")
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
    @Operation(summary = "Dados do usuário autenticado (derivados do servidor)")
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

    public record RefreshTokenRequest(String refreshToken) {}
}
