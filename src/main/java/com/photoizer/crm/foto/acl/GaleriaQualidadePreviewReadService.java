package com.photoizer.crm.foto.acl;

import com.photoizer.crm.shared.processing.QualidadePreview;

import java.util.UUID;

/**
 * PORTA: Anti-Corruption Layer — lê a qualidade de prévia configurada na
 * galeria sem acoplar o módulo foto ao ecommerce.
 */
public interface GaleriaQualidadePreviewReadService {

    QualidadePreview qualidadePreview(UUID agendamentoId);
}
