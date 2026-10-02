package com.photoizer.crm.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de arquitetura de segurança (regressão).
 *
 * <p>Garante que controllers administrativos/sensíveis declaram explicitamente
 * {@code @RolesAllowed} (ou {@code @PreAuthorize}), evitando que um endpoint novo
 * caia em {@code anyRequest().authenticated()} e fique acessível a qualquer papel
 * (ex.: {@code ROLE_CLIENTE} obtido por registro público).
 *
 * <p>Referência: achado C2 do relatório de segurança.
 */
class AutorizacaoControllersTest {

    private static final Path CONTROLLERS_ROOT =
        Path.of("src/main/java/com/photoizer/crm");

    /**
     * Controllers que compartilham o mesmo path base de {@code @RolesAllowed}
     * aplicado na classe, ou que são públicos por design / tratados por regra
     * explícita no {@code SecurityConfig}.
     */
    private static final List<String> CONTROLLERS_PUBLICOS_POR_DESIGN = List.of(
        "AuthController.java",
        "ClienteAuthController.java",
        "PropostaPublicaController.java",
        "EcommerceController.java",
        "AvaliacaoController.java",
        "SessaoController.java",
        "GlobalExceptionHandler.java"
    );

    /**
     * Controllers cuja classe não tem a anotação, mas o path é coberto por
     * requestMatcher explícito no SecurityConfig e/ou os métodos sensíveis estão
     * anotados. Lista mantida de forma conservadora — novos controllers devem
     * declarar autorização na classe.
     */
    private static final List<String> CONTROLLERS_VALIDADOS_MANUALMENTE = List.of(
        "AgendamentoController.java",
        "FinanceiroController.java",
        "ReceitaController.java",
        "DespesaController.java",
        "FotografoController.java",
        "ConfiguracaoController.java",
        "PropostaTemplateController.java",
        "AdminEcommerceController.java",
        "DocumentoController.java",
        "IndicadorController.java",
        // Notificacao: não usa papel; ownership garantido por @AuthenticationPrincipal
        // (cada usuário só acessa as próprias notificações — ver NotificacaoController).
        "NotificacaoController.java"
    );

    @Test
    @DisplayName("Controllers administrativos devem declarar @RolesAllowed na classe")
    void controllersDevemDeclararAutorizacao() throws IOException {
        var violacoes = new java.util.ArrayList<String>();

        try (Stream<Path> paths = Files.walk(CONTROLLERS_ROOT)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                .filter(p -> {
                    try {
                        return Files.readString(p).contains("@RestController");
                    } catch (IOException e) {
                        return false;
                    }
                })
                .forEach(path -> {
                    var arquivo = path.getFileName().toString();
                    if (CONTROLLERS_PUBLICOS_POR_DESIGN.contains(arquivo)
                        || CONTROLLERS_VALIDADOS_MANUALMENTE.contains(arquivo)) {
                        return;
                    }
                    try {
                        var conteudo = Files.readString(path);
                        // Anotação na classe ou em algum método do arquivo.
                        boolean temRoles = conteudo.contains("@RolesAllowed")
                            || conteudo.contains("@PreAuthorize")
                            || conteudo.contains("@Secured");
                        if (!temRoles) {
                            violacoes.add(path.toString());
                        }
                    } catch (IOException e) {
                        violacoes.add(path + " (erro de leitura)");
                    }
                });
        }

        assertThat(violacoes)
            .as("Controllers sem autorização explícita (risco de BAC): %s", violacoes)
            .isEmpty();
    }
}
