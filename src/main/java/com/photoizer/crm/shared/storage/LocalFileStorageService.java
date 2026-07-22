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

    public LocalFileStorageService(@Value("${app.storage.upload-dir:uploads}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
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

        var nomeArquivo = UUID.randomUUID() + "_" + arquivo.getOriginalFilename();
        var caminho = uploadDir.resolve(nomeArquivo);

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

        var ext = "";
        var originalName = arquivo.getOriginalFilename();
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
        }
        var nomeArquivo = prefix + "_" + UUID.randomUUID() + ext;
        var caminho = subDir.resolve(nomeArquivo);

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
            Files.deleteIfExists(Path.of(caminho));
        } catch (IOException e) {
            throw new RuntimeException("Erro ao deletar arquivo: " + caminho, e);
        }
    }

    @Override
    public Path getUploadDir() {
        return uploadDir;
    }
}
