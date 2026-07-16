package com.photoizer.crm.shared.storage;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    String salvar(MultipartFile arquivo);

    void deletar(String caminho);
}
