package com.photoizer.crm.auth.config;

import com.photoizer.crm.shared.config.CorsConfig;
import com.photoizer.crm.shared.config.RateLimitFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final CorsConfig corsConfig;
    private final RateLimitFilter rateLimitFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter, CorsConfig corsConfig, RateLimitFilter rateLimitFilter) {
        this.jwtFilter = jwtFilter;
        this.corsConfig = corsConfig;
        this.rateLimitFilter = rateLimitFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(c -> c.configurationSource(corsConfig.corsConfigurationSource()))
            .csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/login").permitAll()
                .requestMatchers("/api/v1/auth/cliente/login").permitAll()
                .requestMatchers("/api/v1/auth/cliente/registro").permitAll()
                .requestMatchers("/api/v1/ecommerce/galeria/**").permitAll()
                .requestMatchers("/api/v1/ecommerce/fotos/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/ecommerce/sessao").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/ecommerce/admin/compras/**").authenticated()
                .requestMatchers("/api/v1/ecommerce/admin/**").authenticated()
                .requestMatchers("/h2-console/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/v1/admin/**", "/api/v1/financeiro/**", "/api/v1/configuracoes/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/edicao/*/raw").hasAnyRole("ADMIN", "FOTOGRAFO")
                .requestMatchers(HttpMethod.GET, "/api/v1/edicao/*/download-raw", "/api/v1/edicao/*/download-editadas").hasAnyRole("ADMIN", "EDITOR")
                .requestMatchers(HttpMethod.POST, "/api/v1/edicao/*/editadas").hasAnyRole("ADMIN", "EDITOR")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/edicao/*/concluir").hasAnyRole("ADMIN", "EDITOR")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/edicao/*/publicar").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/edicao/*/publicar-loja").hasAnyRole("ADMIN", "FOTOGRAFO")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/edicao/fotos/*").hasAnyRole("ADMIN", "EDITOR")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/edicao/*/observacoes").hasAnyRole("ADMIN", "EDITOR")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/edicao/fotos/reordenar").hasAnyRole("ADMIN", "EDITOR")
                .requestMatchers(HttpMethod.GET, "/api/v1/edicao/**").authenticated()
                .requestMatchers("/api/v1/edicao/**").hasAnyRole("ADMIN", "FOTOGRAFO", "EDITOR")
                .requestMatchers(HttpMethod.GET, "/api/v1/agendamentos/*/fotos/*/original")
                    .hasAnyRole("ADMIN", "FOTOGRAFO", "EDITOR", "AGENDADOR")
                .requestMatchers("/api/v1/agendamentos/**").authenticated()
                .requestMatchers("/api/v1/clientes/**").authenticated()
                .requestMatchers("/api/v1/pacotes/**").authenticated()
                .requestMatchers("/api/v1/comissoes/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/users/**").authenticated()
                .requestMatchers("/api/v1/users/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/dashboard/**").hasAnyRole("ADMIN", "FOTOGRAFO", "EDITOR")
                .requestMatchers("/api/v1/config/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/despesas/**").hasAnyRole("ADMIN", "FOTOGRAFO", "EDITOR")
                .requestMatchers("/api/v1/indicacoes/**", "/api/v1/indicadores/**").hasAnyRole("ADMIN", "FOTOGRAFO", "EDITOR")
                .requestMatchers("/api/v1/rascunhos/**").authenticated()
                .requestMatchers("/api/v1/notificacoes/**").authenticated()
                .requestMatchers("/api/v1/usuarios/**").authenticated()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/avaliacoes/depoimentos").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/avaliacoes").permitAll()
                .requestMatchers("/api/v1/avaliacoes/**").authenticated()
                .requestMatchers("/api/v1/sessoes/**").hasAnyRole("ADMIN", "FOTOGRAFO", "EDITOR", "AGENDADOR")
                .anyRequest().denyAll()
            )
            .headers(h -> h.frameOptions(f -> f.sameOrigin()))
            .exceptionHandling(e -> e
                .authenticationEntryPoint((request, response, authException) ->
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
                .accessDeniedHandler((request, response, accessDeniedException) ->
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden")))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
