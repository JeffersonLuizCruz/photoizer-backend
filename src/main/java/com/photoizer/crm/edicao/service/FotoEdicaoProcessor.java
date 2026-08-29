package com.photoizer.crm.edicao.service;

import com.photoizer.crm.foto.service.FotoProcessingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.UUID;

/**
 * PATTERN: Template Method — centraliza processamento de imagem (watermark + thumbnail).
 * Delega para FotoProcessingHelper (módulo foto) que centraliza constantes e fallback.
 * Elimina duplicação que existia em PublicacaoService e EdicaoRevisaoService.
 */
@Service
public class FotoEdicaoProcessor {

    private static final Logger log = LoggerFactory.getLogger(FotoEdicaoProcessor.class);

    private final FotoProcessingHelper fotoProcessingHelper;

    public FotoEdicaoProcessor(FotoProcessingHelper fotoProcessingHelper) {
        this.fotoProcessingHelper = fotoProcessingHelper;
    }

    public FotoProcessingHelper.ProcessedImages processar(Path editedPath, UUID fotoId) {
        var targetDir = editedPath.getParent();
        return fotoProcessingHelper.processar(editedPath, targetDir, fotoId);
    }
}
