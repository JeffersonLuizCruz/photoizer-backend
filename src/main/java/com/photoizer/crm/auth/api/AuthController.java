package com.photoizer.crm.auth.api;

import com.photoizer.crm.auth.service.AuthService;
import com.photoizer.crm.auth.service.RefreshTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticação", description = "Login, refresh token e logout")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;

    public AuthController(AuthService authService, RefreshTokenService refreshTokenService) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/login")
    @Operation(summary = "Autenticar administrador")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar access token usando refresh token")
    public ResponseEntity<Map<String, String>> refresh(@RequestBody RefreshTokenRequest request) {
        var newAccessToken = refreshTokenService.refreshAccessToken(request.refreshToken());
        return ResponseEntity.ok(Map.of("accessToken", newAccessToken));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revogar tokens (access + refresh)")
    public ResponseEntity<Void> logout(@RequestBody RefreshTokenRequest request,
                                        Authentication authentication) {
        if (authentication != null && authentication.getCredentials() != null) {
            refreshTokenService.blockAccessToken(authentication.getCredentials().toString());
        }
        refreshTokenService.revokeRefreshToken(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    public record RefreshTokenRequest(String refreshToken) {}
}
