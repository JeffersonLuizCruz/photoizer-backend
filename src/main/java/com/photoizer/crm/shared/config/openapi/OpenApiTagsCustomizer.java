package com.photoizer.crm.shared.config.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

/**
 * Substitui a lista final de tags pela lista canônica ({@link OpenApiTags#all()}).
 *
 * <p>Necessário porque o SpringDoc mescla as tags declaradas no bean {@code OpenAPI}
 * com as declaradas em cada {@code @Tag} de controller usando {@code Tag.equals()},
 * que compara <strong>nome e descrição</strong>. Descrições divergentes geram tags
 * duplicadas e a ordem final fica indefinida (baseada em {@code HashSet}).
 *
 * <p>Esta customização roda ao final da geração e garante: tags únicas, descrições
 * canônicas e ordem determinística — essencial para versionar {@code openapi.json}.
 */
@Component
public class OpenApiTagsCustomizer implements OpenApiCustomizer {

    @Override
    public void customise(OpenAPI openAPI) {
        openAPI.setTags(OpenApiTags.all());
    }
}
