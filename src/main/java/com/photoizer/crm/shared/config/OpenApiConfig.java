package com.photoizer.crm.shared.config;

import com.photoizer.crm.shared.api.PageResponse;
import com.photoizer.crm.shared.exception.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuração central do OpenAPI/Swagger.
 *
 * <p>Concentra metadados (info, servers, tags), os esquemas de segurança e os
 * componentes reutilizáveis ({@link ErrorResponse}, {@link PageResponse}).
 * As respostas de erro padrão e o requisito de segurança por endpoint são
 * aplicados automaticamente por {@code GlobalOpenApiCustomizer}.
 */
@Configuration
public class OpenApiConfig {

    /** Esquema HTTP Bearer (JWT via header {@code Authorization}). */
    public static final String SECURITY_SCHEME_BEARER = "bearerAuth";

    /** Esquema de cookie HttpOnly emitido no login ({@code photoizer_access}). */
    public static final String SECURITY_SCHEME_COOKIE = "cookieAuth";

    private static final String REF_PREFIX = Components.COMPONENTS_SCHEMAS_REF;

    @Bean
    public OpenAPI openAPI(@Value("${info.app.version:1.0.0}") String version) {
        return new OpenAPI()
            .info(new Info()
                .title("Photoizer CRM API")
                .version(version)
                .description("""
                    API REST do CRM Photoizer para estúdio de fotografia: clientes, agenda de
                    ensaios, pacotes, fotos, edição, financeiro, e-commerce (galeria pública e
                    venda de fotos extras), comissões e notificações.

                    **Autenticação:** o login emite cookies HttpOnly (access/refresh) e o
                    frontend envia um header CSRF `X-XSRF-TOKEN` nas mutações. O header
                    `Authorization: Bearer <jwt>` também é aceito para integrações.

                    **Códigos de erro:** as respostas de erro seguem o schema `ErrorResponse`,
                    com o campo `code` correspondente ao enum `ErrorCode` (ex.: `CLIENTE_NAO_ENCONTRADO`).
                    """)
                .contact(new Contact().name("Photoizer").url("https://photoizer.com.br"))
                .license(new License().name("Proprietário")))
            .servers(List.of(
                new Server().url("/").description("Servidor atual"),
                new Server().url("http://localhost:8080").description("Desenvolvimento local")))
            .components(new Components()
                .addSecuritySchemes(SECURITY_SCHEME_BEARER, new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("JWT de acesso (compatibilidade para integrações server-to-server)."))
                .addSecuritySchemes(SECURITY_SCHEME_COOKIE, new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.COOKIE)
                    .name("photoizer_access")
                    .description("Cookie HttpOnly emitido no login do staff."))
                .schemas(componentSchemas()));
    }

    private Map<String, Schema> componentSchemas() {
        // Registra ErrorResponse (+ FieldError) e PageResponse como componentes
        // nomeados para que possam ser referenciados por $ref no customizer.
        var schemas = new LinkedHashMap<String, Schema>();
        schemas.putAll(ModelConverters.getInstance().readAll(ErrorResponse.class));
        schemas.putAll(ModelConverters.getInstance().readAll(PageResponse.class));
        return schemas;
    }

    /** Nome do schema reutilizável do contrato de erro. */
    public static String errorRef() {
        return REF_PREFIX + ErrorResponse.class.getSimpleName();
    }
}
