package com.photoizer.crm.tools;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ferramenta de uso unico: exporta a especificação OpenAPI gerada em runtime
 * para {@code docs/api/openapi.json} (artefato versionado consumido por
 * integrações e validado em CI).
 *
 * <p>NÃO é um teste funcional: o nome não termina em "Test" de propósito, então
 * o Surefire não o executa no build padrão. Rode explicitamente:
 *
 * <pre>mvn -Dtest=OpenApiSpecExporter test</pre>
 *
 * <p>A geração usa H2 em memória (perfil {@code test}), sem Postgres.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiSpecExporter {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exportar() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        // Reindenta para que o artefato versionado tenha diffs legíveis linha a linha.
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String pretty = mapper
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(mapper.readTree(json));

        Path out = Path.of("docs", "api", "openapi.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, pretty + System.lineSeparator());
        System.out.println("OpenAPI gerado em: " + out.toAbsolutePath());
    }
}
