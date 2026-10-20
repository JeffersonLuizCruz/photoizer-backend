package com.photoizer.crm.auth.config;

import com.photoizer.crm.shared.config.CorsConfig;
import com.photoizer.crm.shared.config.RateLimitFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
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
    private final CsrfCookieFilter csrfCookieFilter;
    private final CorsConfig corsConfig;
    private final RateLimitFilter rateLimitFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter,
                          CsrfCookieFilter csrfCookieFilter,
                          CorsConfig corsConfig,
                          RateLimitFilter rateLimitFilter) {
        this.jwtFilter = jwtFilter;
        this.csrfCookieFilter = csrfCookieFilter;
        this.corsConfig = corsConfig;
        this.rateLimitFilter = rateLimitFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, Environment environment) throws Exception {
        boolean devProfile = environment.matchesProfiles("dev", "local", "test");
        boolean homologProfile = environment.matchesProfiles("homolog");

        http
            .cors(c -> c.configurationSource(corsConfig.corsConfigurationSource()))
            .csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                auth
                    .requestMatchers("/api/v1/auth/login").permitAll()
                    .requestMatchers("/api/v1/auth/refresh").permitAll()
                    .requestMatchers("/api/v1/auth/cliente/login").permitAll()
                    .requestMatchers("/api/v1/auth/cliente/registro").permitAll()
                    .requestMatchers("/api/v1/ecommerce/galeria/**").permitAll()
                    .requestMatchers("/api/v1/ecommerce/fotos/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/ecommerce/sessao").permitAll()
                    .requestMatchers("/api/v1/propostas/publico/**").permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/avaliacoes/depoimentos").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/avaliacoes").permitAll()
                    .requestMatchers("/actuator/health").permitAll();

                // Swagger/OpenAPI por ambiente:
                //  - dev/local/test: UI e spec livres (permitAll).
                //  - homolog: UI e spec exigem staff autenticado (validar contrato sem expor público).
                //  - prod: bloqueados (denyAll); consumo via docs/api/openapi.json versionado.
                // H2 console permanece apenas em desenvolvimento.
                if (devProfile) {
                    auth.requestMatchers("/h2-console/**").permitAll();
                    auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html",
                        "/v3/api-docs*", "/v3/api-docs/**").permitAll();
                } else if (homologProfile) {
                    auth.requestMatchers("/h2-console/**").denyAll();
                    auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html",
                        "/v3/api-docs*", "/v3/api-docs/**").authenticated();
                } else {
                    auth.requestMatchers("/h2-console/**").denyAll();
                    auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html",
                        "/v3/api-docs*", "/v3/api-docs/**").denyAll();
                }

                auth
                    .requestMatchers("/api/v1/dev/**").hasRole("ADMIN")
                    .requestMatchers("/api/v1/agendamentos/**").hasAnyRole("ADMIN", "FOTOGRAFO", "EDITOR", "AGENDADOR")
                    .requestMatchers("/api/v1/bloqueios/**").hasAnyRole("ADMIN", "FOTOGRAFO", "EDITOR", "AGENDADOR")
                    .requestMatchers("/api/v1/sessoes/**").hasAnyRole("ADMIN", "FOTOGRAFO", "EDITOR", "AGENDADOR")
                    // fail-safe: qualquer rota não classificada exige autenticação; os
                    // controllers administrativos declaram @RolesAllowed explicitamente.
                    .anyRequest().authenticated();
            })
            .headers(h -> h
                .frameOptions(f -> f.sameOrigin())
                .contentTypeOptions(ct -> {})
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000))
                .referrerPolicy(rp -> rp.policy(
                    org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; frame-ancestors 'self'; object-src 'none'; base-uri 'self'")))
            .exceptionHandling(e -> e
                .authenticationEntryPoint((request, response, authException) ->
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
                .accessDeniedHandler((request, response, accessDeniedException) ->
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden")))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(csrfCookieFilter, JwtAuthenticationFilter.class)
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
