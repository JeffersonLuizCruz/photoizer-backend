package com.photoizer.crm.shared.storage;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Valida o conteúdo binário (magic bytes / assinatura de arquivo) de uploads.
 *
 * <p>A validação por extensão e {@code Content-Type} é trivialmente contornável
 * (ambos são controlados pelo cliente). Este componente lê os primeiros bytes do
 * arquivo e confere a assinatura real do formato, mitigando upload de polyglots
 * e de conteúdo disfarçado (ex.: {@code .jpg} contendo HTML/SVG/PDF malicioso).
 *
 * <p>Referência: achado M3 do relatório de segurança.
 */
@Component
public class FileSignatureValidator {

    private static final int HEADER_SIZE = 32;

    /** Assinaturas conhecidas por extensão (prefixos hexadecimais aceitos). */
    private static final List<SignatureRule> RULES = List.of(
        // JPEG: FF D8 FF
        new SignatureRule(Set.of(".jpg", ".jpeg"), "FFD8FF"),
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        new SignatureRule(Set.of(".png"), "89504E470D0A1A0A"),
        // GIF: "GIF87a" ou "GIF89a"
        new SignatureRule(Set.of(".gif"), "474946383761", "474946383961"),
        // WEBP: container "RIFF"
        new SignatureRule(Set.of(".webp"), "52494646"),
        // BMP: "BM"
        new SignatureRule(Set.of(".bmp"), "424D"),
        // PDF: "%PDF-"
        new SignatureRule(Set.of(".pdf"), "255044462D"),
        // ZIP e formatos RAW/PSD/TIFF que usam container ZIP (PK\x03\x04, PK\x05\x06, PK\x07\x08)
        new SignatureRule(Set.of(".zip", ".psd", ".cr2", ".nef", ".arw", ".raf",
                ".dng", ".orf", ".rw2", ".pef"), "504B0304", "504B0506", "504B0708")
    );

    /**
     * Valida a assinatura binária do arquivo para a extensão informada.
     * Lança {@link IllegalArgumentException} se o conteúdo não corresponder.
     */
    public void validateContent(MultipartFile file, String originalFilename) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Arquivo é obrigatório");
        }
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("Nome do arquivo é obrigatório");
        }

        var ext = extensionOf(originalFilename);
        var regrasRelevantes = RULES.stream()
            .filter(r -> r.extensions().contains(ext))
            .toList();

        if (regrasRelevantes.isEmpty()) {
            // Sem regra específica (ex.: .tif/.tiff): assinatura não aplicável;
            // extensão + contexto continuam valendo.
            return;
        }

        var headerHex = toHex(readHeader(file));
        var valido = regrasRelevantes.stream()
            .flatMap(r -> r.prefixes().stream())
            .anyMatch(headerHex::startsWith);

        if (!valido) {
            throw new IllegalArgumentException(
                "Conteúdo do arquivo não corresponde à extensão " + ext
                    + ". Upload rejeitado por segurança.");
        }
    }

    private byte[] readHeader(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(HEADER_SIZE);
        } catch (IOException e) {
            throw new IllegalArgumentException("Não foi possível ler o arquivo enviado");
        }
    }

    private String extensionOf(String filename) {
        var idx = filename.lastIndexOf('.');
        return idx < 0 ? "" : filename.substring(idx).toLowerCase();
    }

    private static String toHex(byte[] bytes) {
        var sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString().toUpperCase();
    }

    private record SignatureRule(Set<String> extensions, List<String> prefixes) {
        SignatureRule(Set<String> extensions, String... prefixes) {
            this(extensions, Arrays.asList(prefixes));
        }
    }
}
