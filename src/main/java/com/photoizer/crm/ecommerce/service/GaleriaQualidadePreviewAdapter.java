package com.photoizer.crm.ecommerce.service;

import com.photoizer.crm.foto.acl.GaleriaQualidadePreviewReadService;
import com.photoizer.crm.shared.processing.QualidadePreview;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Adapter do ecommerce para a porta de qualidade da prévia.
 *
 * <p>Galerias sem linha configurada ou sem valor explícito usam
 * {@link QualidadePreview#padrao()} (ORIGINAL).
 */
@Service
public class GaleriaQualidadePreviewAdapter implements GaleriaQualidadePreviewReadService {

    private final GaleriaEnsaioService galeriaEnsaioService;

    public GaleriaQualidadePreviewAdapter(GaleriaEnsaioService galeriaEnsaioService) {
        this.galeriaEnsaioService = galeriaEnsaioService;
    }

    @Override
    public QualidadePreview qualidadePreview(UUID agendamentoId) {
        return galeriaEnsaioService.obter(agendamentoId).qualidadePreviewEfetiva();
    }
}
