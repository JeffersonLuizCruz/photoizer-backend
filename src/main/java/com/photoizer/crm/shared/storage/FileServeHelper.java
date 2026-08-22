package com.photoizer.crm.shared.storage;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * PATTERN: Helper Pattern
 *
 * Centraliza a lógica de servir arquivos do filesystem via HTTP.
 * Elimina duplicação entre DocumentoController e ContratoController.
 *
 * Responsabilidades:
 * - Validação contra path traversal (previne ataques de adulteração de banco)
 * - Resolução de content-type via Files.probeContentType
 * - Montagem de resposta HTTP com headers adequados
 *
 * Motivo: A lógica de download de arquivos estava duplicada em dois controllers
 * com pequenas diferenhas (disposition, validação de segurança). O Helper
 * garante comportamento consistente e ponto único de manutenção.
 */
@Component
public class FileServeHelper {

    private static final String UPLOAD_DIR = "uploads";

    /**
     * Serve um arquivo do filesystem com validação de segurança.
     *
     * @param caminho caminho do arquivo (absoluto ou relativo ao diretório de uploads)
     * @param filename nome do arquivo para Content-Disposition
     * @param disposition "inline" (visualiza no browser) ou "attachment" (força download)
     * @return ResponseEntity com o arquivo ou 404/400 conforme validação
     */
    public ResponseEntity<Resource> servirArquivo(String caminho, String filename, String disposition) {
        if (caminho == null || caminho.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        var uploadDir = Path.of(UPLOAD_DIR).toAbsolutePath().normalize();
        var file = Path.of(caminho).toAbsolutePath().normalize();

        if (!file.startsWith(uploadDir)) {
            return ResponseEntity.badRequest().build();
        }

        var resource = new FileSystemResource(file);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        var contentType = resolverContentType(caminho);

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                disposition + "; filename=\"" + filename + "\"")
            .contentType(contentType)
            .body(resource);
    }

    /**
     * Resolve content-type do arquivo via probe do sistema operacional.
     * Fallback para APPLICATION_OCTET_STREAM se não conseguir detectar.
     */
    public MediaType resolverContentType(String caminho) {
        try {
            var probe = Files.probeContentType(Path.of(caminho));
            if (probe != null) {
                return MediaType.parseMediaType(probe);
            }
        } catch (Exception ignored) {
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    /**
     * Extrai a extensão do arquivo a partir do caminho.
     * Retorna ".bin" se não conseguir determinar.
     */
    public String extrairExtensao(String caminho) {
        if (caminho == null) return ".bin";
        var nome = Path.of(caminho).getFileName().toString();
        var idx = nome.lastIndexOf('.');
        return idx >= 0 ? nome.substring(idx) : ".bin";
    }
}
