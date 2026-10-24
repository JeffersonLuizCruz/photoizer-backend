package com.photoizer.crm.auth.config;

import com.photoizer.crm.auth.service.RefreshTokenService;
import com.photoizer.crm.shared.exception.SecurityErrorHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final AuthCookieService authCookieService;
    private final SecurityErrorHandler securityErrorHandler;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                    RefreshTokenService refreshTokenService,
                                    AuthCookieService authCookieService,
                                    SecurityErrorHandler securityErrorHandler) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.authCookieService = authCookieService;
        this.securityErrorHandler = securityErrorHandler;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        var header = request.getHeader(HEADER);
        String token = null;

        if (header != null && header.startsWith(PREFIX)) {
            token = header.substring(PREFIX.length());
        } else {
            // A3: fallback para cookie HttpOnly (navegador envia automaticamente).
            token = authCookieService.lerAccessToken(request).orElse(null);
        }

        if (token == null || token.isBlank() || !jwtTokenProvider.validateToken(token)) {
            chain.doFilter(request, response);
            return;
        }

        if (jwtTokenProvider.isRefreshToken(token)) {
            chain.doFilter(request, response);
            return;
        }

        if (refreshTokenService.isTokenBlocked(token)) {
            SecurityContextHolder.clearContext();
            securityErrorHandler.escrever(response, HttpStatus.UNAUTHORIZED, "Token revogado");
            return;
        }

        var userId = jwtTokenProvider.getUserIdFromToken(token);
        var papel = jwtTokenProvider.getPapelFromToken(token);

        var authentication = new UsernamePasswordAuthenticationToken(
            userId, token, List.of(() -> "ROLE_" + papel));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }
}
