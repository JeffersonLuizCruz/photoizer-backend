package com.photoizer.crm.shared.storage;

import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.UUID;

public interface FileStorageService {

    String salvar(MultipartFile arquivo);

    String salvarEmSubdiretorio(MultipartFile arquivo, UUID agendamentoId, String prefix);

    void deletar(String caminho);

    Path getUploadDir();
}
