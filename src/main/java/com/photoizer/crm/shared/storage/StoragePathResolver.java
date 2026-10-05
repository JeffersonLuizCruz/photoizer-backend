package com.photoizer.crm.shared.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Resolve caminhos de arquivos armazenados no banco de forma portavel.
 *
 * <p>Os caminhos sao persistidos de forma <b>relativa</b> ao diretorio de uploads
 * ({@code app.storage.upload-dir}). Isso permite mover a aplicacao entre ambientes
 * e containers sem quebrar referencias. Por compatibilidade, caminhos absolutos
 * previamente gravados tambem sao aceitos.
 */
@Component
public class StoragePathResolver {

    private final Path uploadDir;

    public StoragePathResolver(@Value("${app.storage.upload-dir:uploads}") String uploadDir) {
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    /**
     * Resolve um caminho armazenado para um {@link Path} absoluto sob o diretorio de uploads.
     *
     * @param caminhoArmazenado caminho relativo (formato atual) ou absoluto (legado)
     * @return path absoluto normalizado, ou {@code null} se a entrada for vazia
     */
    public Path resolver(String caminhoArmazenado) {
        if (caminhoArmazenado == null || caminhoArmazenado.isBlank()) {
            return null;
        }
        var path = Path.of(caminhoArmazenado);
        return path.isAbsolute() ? path.normalize() : uploadDir.resolve(path).normalize();
    }

    /**
     * Converte um caminho absoluto em relativo ao diretorio de uploads, para persistencia.
     *
     * @throws IllegalArgumentException se o caminho estiver fora do diretorio de uploads
     */
    public String relativo(Path caminhoAbsoluto) {
        var abs = caminhoAbsoluto.toAbsolutePath().normalize();
        if (!abs.startsWith(uploadDir)) {
            throw new IllegalArgumentException("Caminho fora do diretorio de uploads: " + caminhoAbsoluto);
        }
        return uploadDir.relativize(abs).toString();
    }

    public Path getUploadDir() {
        return uploadDir;
    }
}
