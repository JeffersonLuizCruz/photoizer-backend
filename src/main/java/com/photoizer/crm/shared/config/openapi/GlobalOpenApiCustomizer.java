package com.photoizer.crm.shared.config.openapi;

import com.photoizer.crm.shared.config.OpenApiConfig;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import jakarta.annotation.security.RolesAllowed;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.method.HandlerMethod;

/**
 * Customizer global do OpenAPI.
 *
 * <p>Aplica a convenção transversal a <strong>todos</strong> os endpoints, sem
 * exigir repetição nas anotações dos controllers:
 *
 * <ul>
 *   <li>Endpoints protegidos (classe/método com {@code @RolesAllowed}) recebem o
 *       requisito de segurança (Bearer + cookie) e as respostas {@code 401}/{@code 403}.</li>
 *   <li>Endpoints com {@code @RequestBody} recebem a resposta {@code 422}
 *       (erro de validação — ver {@code GlobalExceptionHandler}).</li>
 *   <li>Todos recebem {@code 500} como fallback documentado.</li>
 * </ul>
 *
 * Respostas já declaradas explicitamente no controller têm precedência.
 */
@Component
public class GlobalOpenApiCustomizer implements OperationCustomizer {

    private static final String JSON = "application/json";

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }

        if (isSecured(handlerMethod)) {
            operation.addSecurityItem(new SecurityRequirement().addList(OpenApiConfig.SECURITY_SCHEME_BEARER));
            operation.addSecurityItem(new SecurityRequirement().addList(OpenApiConfig.SECURITY_SCHEME_COOKIE));
            addIfAbsent(operation, "401", "Não autenticado");
            addIfAbsent(operation, "403", "Acesso negado ao recurso");
        }

        if (hasRequestBody(handlerMethod)) {
            addIfAbsent(operation, "422", "Um ou mais campos estão inválidos");
        }

        addIfAbsent(operation, "500", "Erro interno do servidor");

        return operation;
    }

    private boolean isSecured(HandlerMethod handlerMethod) {
        return handlerMethod.getMethodAnnotation(RolesAllowed.class) != null
            || handlerMethod.getBeanType().isAnnotationPresent(RolesAllowed.class);
    }

    private boolean hasRequestBody(HandlerMethod handlerMethod) {
        for (var parameter : handlerMethod.getMethodParameters()) {
            if (parameter.hasParameterAnnotation(RequestBody.class)) {
                return true;
            }
        }
        return false;
    }

    private void addIfAbsent(Operation operation, String code, String description) {
        if (!operation.getResponses().containsKey(code)) {
            operation.getResponses().addApiResponse(code, errorResponse(description));
        }
    }

    private ApiResponse errorResponse(String description) {
        return new ApiResponse()
            .description(description)
            .content(new Content().addMediaType(JSON,
                new MediaType().schema(new ObjectSchema().$ref(OpenApiConfig.errorRef()))));
    }
}
