package com.photoizer.crm.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("CarolCRM API")
                .description("API de gestão de ensaios fotográficos")
                .version("1.0.0")
                .contact(new Contact().name("CarolCRM")));
    }
}
