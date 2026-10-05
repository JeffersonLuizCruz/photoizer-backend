package com.photoizer.crm.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regressão da migração {@code V2__seed_usuarios_iniciais.sql}: garante que o
 * banco nasce com ADMIN e AGENDADOR e que os hashes BCrypt correspondem à senha
 * padrão documentada ({@code dev123}). Uma senha errada no SQL impediria o login
 * do primeiro acesso, então vale travar via teste.
 */
class SeedUsuariosIniciaisMigrationTest {

    private static final Path MIGRATION =
        Path.of("src/main/resources/db/migration/V2__seed_usuarios_iniciais.sql");

    private static final Pattern BCRYPT = Pattern.compile("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}");

    @Test
    @DisplayName("V2 semeia admin e agendador com senha dev123")
    void deveSemearUsuariosIniciais() throws IOException {
        var sql = Files.readString(MIGRATION);

        assertThat(sql).contains("admin@photoizer.com");
        assertThat(sql).contains("agendamento@photoizer.com");
        assertThat(sql).contains("'ADMIN'");
        assertThat(sql).contains("'AGENDADOR'");
        assertThat(sql).contains("ON CONFLICT (email) DO NOTHING");

        var encoder = new BCryptPasswordEncoder();
        var matcher = BCRYPT.matcher(sql);
        var hashes = new java.util.ArrayList<String>();
        while (matcher.find()) {
            hashes.add(matcher.group());
        }

        assertThat(hashes).as("hashes BCrypt encontrados na V2").hasSizeGreaterThanOrEqualTo(2);
        assertThat(List.copyOf(hashes))
            .as("todos os hashes devem casar com a senha padrão dev123")
            .allMatch(hash -> encoder.matches("dev123", hash));
    }
}
