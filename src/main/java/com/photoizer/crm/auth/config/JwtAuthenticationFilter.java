package com.photoizer.crm.auth.config;

import com.photoizer.crm.auth.service.RefreshTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                    RefreshTokenService refreshTokenService,
                                    AuthCookieService authCookieService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.authCookieService = authCookieService;
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
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token revogado");
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
