package com.photoizer.crm.shared.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class LocalFileStorageService implements FileStorageService {

    private final Path uploadDir;
    private final FileValidator fileValidator;

    public LocalFileStorageService(
            @Value("${app.storage.upload-dir:uploads}") String uploadDir,
            FileValidator fileValidator) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.fileValidator = fileValidator;
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("Não foi possível criar o diretório de uploads: " + this.uploadDir, e);
        }
    }

    @Override
    public String salvar(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new IllegalArgumentException("Arquivo é obrigatório");
        }

        // Sanitiza o nome original (previne path traversal)
        var safeName = fileValidator.sanitizeFilename(arquivo.getOriginalFilename());
        var nomeArquivo = UUID.randomUUID() + "_" + safeName;
        var caminho = uploadDir.resolve(nomeArquivo).normalize();

        // Verifica que o caminho resolvido permanece dentro de uploadDir
        fileValidator.validateResolvedPath(caminho, uploadDir);

        try {
            Files.copy(arquivo.getInputStream(), caminho, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Erro ao salvar arquivo: " + nomeArquivo, e);
        }

        return caminho.toString();
    }

    @Override
    public String salvarEmSubdiretorio(MultipartFile arquivo, UUID agendamentoId, String prefix) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new IllegalArgumentException("Arquivo é obrigatório");
        }

        var subDir = uploadDir.resolve(agendamentoId.toString());
        try {
            Files.createDirectories(subDir);
        } catch (IOException e) {
            throw new RuntimeException("Erro ao criar diretório: " + subDir, e);
        }

        // Extrai extensão do nome sanitizado
        var safeName = fileValidator.sanitizeFilename(arquivo.getOriginalFilename());
        var ext = "";
        if (safeName.contains(".")) {
            ext = safeName.substring(safeName.lastIndexOf(".")).toLowerCase();
        }

        var nomeArquivo = prefix + "_" + UUID.randomUUID() + ext;
        var caminho = subDir.resolve(nomeArquivo).normalize();

        // Verifica que o caminho resolvido permanece dentro de uploadDir
        fileValidator.validateResolvedPath(caminho, uploadDir);

        try {
            Files.copy(arquivo.getInputStream(), caminho, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Erro ao salvar arquivo: " + nomeArquivo, e);
        }

        return caminho.toString();
    }

    @Override
    public void deletar(String caminho) {
        try {
            var path = Path.of(caminho).toAbsolutePath().normalize();
            // Verifica que não está deletando fora do uploadDir
            fileValidator.validateResolvedPath(path, uploadDir);
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new RuntimeException("Erro ao deletar arquivo: " + caminho, e);
        }
    }

    @Override
    public Path getUploadDir() {
        return uploadDir;
    }
}
