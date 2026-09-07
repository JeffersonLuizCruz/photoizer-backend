package com.photoizer.crm.shared.storage;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * PATTERN: Validator / Whitelist
 *
 * Centraliza validação de arquivos uploadados.
 * Whitelist de extensões fixa no código (mais segura que config externa).
 *
 * Responsabilidades:
 * - Validar extensão do arquivo contra whitelist por contexto
 * - Sanitizar nomes de arquivo (remover path components, caracteres perigosos)
 * - Validar que caminho resolvido permanece dentro do diretório de uploads
 *
 * Contextos suportados:
 * - "image": jpg, jpeg, png, gif, webp
 * - "raw": cr2, nef, arw, raf, dng, orf, rw2, pef
 * - "edited": tiff, tif, psd, jpg, jpeg, png
 * - "receipt": jpg, jpeg, png, pdf
 * - "any": jpg, jpeg, png, pdf, zip (fallback amplo)
 */
@Component
public class FileValidator {

    private static final Map<String, Set<String>> ALLOWED_EXTENSIONS = Map.of(
        "image",   Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp"),
        "raw",     Set.of(".cr2", ".nef", ".arw", ".raf", ".dng", ".orf", ".rw2", ".pef"),
        "edited",  Set.of(".tiff", ".tif", ".psd", ".jpg", ".jpeg", ".png"),
        "receipt", Set.of(".jpg", ".jpeg", ".png", ".pdf"),
        "any",     Set.of(".jpg", ".jpeg", ".png", ".pdf", ".zip")
    );

    /**
     * Valida o arquivo contra a whitelist de extensões para o contexto informado.
     * Lança IllegalArgumentException se o arquivo for inválido.
     */
    public void validate(MultipartFile file, String context) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Arquivo é obrigatório");
        }

        var allowed = ALLOWED_EXTENSIONS.get(context);
        if (allowed == null) {
            throw new IllegalArgumentException("Contexto de upload inválido: " + context);
        }

        var ext = extractExtension(file.getOriginalFilename());
        if (ext.isEmpty()) {
            throw new IllegalArgumentException("Arquivo deve ter uma extensão");
        }

        if (!allowed.contains(ext.get().toLowerCase())) {
            throw new IllegalArgumentException(
                "Tipo de arquivo não permitido: " + ext.get() + ". Permitidos para " + context + ": " + allowed);
        }
    }

    /**
     * Sanitiza o nome do arquivo original:
     * - Remove componentes de path (previne path traversal)
     * - Remove caracteres especiais, mantendo apenas [a-zA-Z0-9._-]
     * - Remove nomes que começam com . (arquivos ocultos)
     * - Limita a 255 caracteres
     */
    public String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "unknown";
        }

        // Remove path components (previne path traversal)
        var filename = Path.of(originalFilename).getFileName().toString();

        // Remove caracteres perigosos
        var cleaned = filename.replaceAll("[^a-zA-Z0-9.\\-_]", "_");

        // Remove nomes ocultos
        if (cleaned.startsWith(".")) {
            cleaned = "_" + cleaned;
        }

        // Limita tamanho
        if (cleaned.length() > 255) {
            cleaned = cleaned.substring(0, 255);
        }

        return cleaned;
    }

    /**
     * Valida e extrai a extensão do arquivo para uso no armazenamento.
     * Lança IllegalArgumentException se a extensão não for permitida.
     */
    public String validateAndExtractExtension(MultipartFile file, String context) {
        validate(file, context);
        return extractExtension(file.getOriginalFilename())
            .orElseThrow(() -> new IllegalArgumentException("Arquivo deve ter uma extensão"));
    }

    /**
     * Valida que o caminho resolvido permanece dentro do diretório base.
     * Previne path traversal após resolução de caminhos relativos.
     *
     * @param resolvedPath caminho já resolvido via Path.resolve().normalize()
     * @param baseDir diretório base que deve conter o caminho
     * @throws SecurityException se o caminho escapar do diretório base
     */
    public void validateResolvedPath(Path resolvedPath, Path baseDir) {
        if (!resolvedPath.startsWith(baseDir)) {
            throw new SecurityException("Tentativa de path traversal detectada: " + resolvedPath);
        }
    }

    private java.util.Optional<String> extractExtension(String filename) {
        if (filename == null) return java.util.Optional.empty();
        var idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1) return java.util.Optional.empty();
        return java.util.Optional.of(filename.substring(idx));
    }
}
