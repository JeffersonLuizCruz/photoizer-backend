package com.photoizer.crm.shared.logging;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Mascaramento de PII para logs. Best-effort, sem dependências externas.
 *
 * <p>Cobre: CPF/CNPJ (formatado e cru), e-mail, telefone, cartão, JWT/Bearer,
 * pares {@code chave=valor} e chaves JSON sensíveis (senha, token, authorization...).
 *
 * <p>Regras:
 * <ul>
 *   <li>mascara <strong>antes</strong> de truncar (corrige a dívida registrada no
 *       {@code shared/MODULE.md});</li>
 *   <li>funciona com records/DTOs via {@link String#valueOf(Object)} — o
 *       {@code toString()} de um record expõe os campos, e o regex os mascara;</li>
 *   <li>nunca lança exceção: logging não pode derrubar a requisição;</li>
 *   <li>valores monetários ({@code 1234.56}) e UUIDs não são mascarados.</li>
 * </ul>
 *
 * <p><strong>Limite consciente:</strong> nomes e endereços completos não são
 * detectáveis por regex. A regra de ouro é não logar esses campos; use os UUIDs
 * internos ({@code cliente_id}, {@code agendamento_id}).
 */
public final class SensitiveDataMask {

    private static final String MASCARA = "***";
    private static final int LIMITE_ENTRADA = 10_000;
    private static final int LIMITE_SAIDA = 2_000;

    /** cpf=..., "cpf": "...", senha: '...', authorization=... */
    private static final Pattern CHAVE_VALOR = Pattern.compile(
        "(?i)([\"']?\\b(?:cpf|cnpj|email|e-mail|telefone|celular|phone|senha|password|passwd|"
        + "token|access[_-]?token|refresh[_-]?token|authorization|secret|api[_-]?key|"
        + "cvv|cartao|card[_-]?number)\\b[\"']?\\s*[=:]\\s*)([\"']?)([^\"'\\s,;&}]+)([\"']?)");

    private static final Pattern BEARER = Pattern.compile(
        "(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]{8,}");

    private static final Pattern JWT = Pattern.compile(
        "\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\b");

    private static final Pattern EMAIL = Pattern.compile(
        "\\b[\\w.+-]+@[\\w-]+\\.[\\w.-]+\\b");

    private static final Pattern CNPJ = Pattern.compile(
        "\\b\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}\\b");

    private static final Pattern CPF = Pattern.compile(
        "\\b\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}\\b");

    /** Evita falsos positivos com valores monetários (não aceita ponto como separador). */
    private static final Pattern TELEFONE = Pattern.compile(
        "(?<!\\d)(?:\\+?55\\s?)?(?:\\(\\d{2}\\)\\s?|\\d{2}\\s)?9?\\d{4}[-\\s]?\\d{4}(?!\\d)");

    /** CPF/telefone crus (10–11) e cartão (13–19). UUIDs e timestamps não casam. */
    private static final Pattern DIGITOS_PII = Pattern.compile(
        "(?<!\\d)(?:\\d{10,11}|\\d{13,19})(?!\\d)");

    private SensitiveDataMask() {
    }

    public static String mask(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        var texto = input.length() > LIMITE_ENTRADA
            ? input.substring(0, LIMITE_ENTRADA)
            : input;

        // Bearer/JWT primeiro: evita que "authorization: Bearer xxx" deixe o token solto.
        texto = BEARER.matcher(texto).replaceAll("Bearer " + MASCARA);
        texto = JWT.matcher(texto).replaceAll(MASCARA);
        texto = CHAVE_VALOR.matcher(texto)
            .replaceAll(m -> m.group(1) + m.group(2) + MASCARA + m.group(4));
        texto = EMAIL.matcher(texto).replaceAll(m -> mascarEmail(m.group()));
        texto = CNPJ.matcher(texto).replaceAll(MASCARA);
        texto = CPF.matcher(texto).replaceAll(MASCARA);
        texto = TELEFONE.matcher(texto).replaceAll(MASCARA);
        texto = DIGITOS_PII.matcher(texto).replaceAll(MASCARA);

        return truncar(texto, LIMITE_SAIDA);
    }

    /** Mascara o {@code toString()} de qualquer objeto (records, DTOs, coleções). */
    public static String mask(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return mask(String.valueOf(value));
        } catch (RuntimeException e) {
            return value.getClass().getSimpleName();
        }
    }

    public static String maskArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        var texto = Arrays.stream(args)
            .map(arg -> arg == null ? "null" : mask(arg))
            .collect(Collectors.joining(", "));
        return truncar(texto, LIMITE_SAIDA);
    }

    private static String mascarEmail(String email) {
        int at = email.indexOf('@');
        return at <= 0 ? MASCARA : email.charAt(0) + MASCARA + email.substring(at);
    }

    private static String truncar(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
