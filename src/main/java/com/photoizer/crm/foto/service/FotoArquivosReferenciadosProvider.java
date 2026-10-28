package com.photoizer.crm.foto.service;

import com.photoizer.crm.foto.repository.FotoEnsaioRepository;
import com.photoizer.crm.shared.processing.ImageProcessingService;
import com.photoizer.crm.shared.processing.QualidadePreview;
import com.photoizer.crm.shared.storage.ArquivosReferenciadosProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Arquivos referenciados pelo módulo foto: os 8 caminhos de cada foto e os
 * caches derivados que carregam o id da foto no nome.
 */
@Component
public class FotoArquivosReferenciadosProvider implements ArquivosReferenciadosProvider {

    private static final List<String> VERSOES = List.of("ORIGINAL", "EDITADA");

    private final FotoEnsaioRepository fotoEnsaioRepository;

    public FotoArquivosReferenciadosProvider(FotoEnsaioRepository fotoEnsaioRepository) {
        this.fotoEnsaioRepository = fotoEnsaioRepository;
    }

    @Override
    public Collection<String> caminhosReferenciados() {
        var caminhos = new ArrayList<String>();
        for (var foto : fotoEnsaioRepository.caminhosDasFotos()) {
            caminhos.add(foto.getOriginalPath());
            caminhos.add(foto.getWatermarkedPath());
            caminhos.add(foto.getThumbPath());
            caminhos.add(foto.getPreviewPath());
            caminhos.add(foto.getEditadaPath());
            caminhos.add(foto.getEditadaWatermarkedPath());
            caminhos.add(foto.getEditadaThumbPath());
            caminhos.add(foto.getEditadaPreviewPath());

            var pasta = foto.getAgendamentoId().toString();
            for (var versao : VERSOES) {
                // Cada versão fica ao lado do arquivo que a originou: orig/ ou editadas/.
                var pastaBase = "EDITADA".equals(versao)
                    ? pasta + "/editadas/"
                    : pasta + "/orig/";
                for (var qualidade : QualidadePreview.values()) {
                    caminhos.add(pastaBase + qualidade.getPrefixoCache()
                        + foto.getId() + "_" + versao + ".jpg");
                }
                caminhos.add(pastaBase + "thumb_protegido_"
                    + ImageProcessingService.PUBLIC_THUMB_VERSION + "_"
                    + foto.getId() + "_" + versao + ".jpg");
                caminhos.add(pastaBase + "hero_" + foto.getId() + "_" + versao + ".jpg");
            }
        }
        return caminhos;
    }
}
