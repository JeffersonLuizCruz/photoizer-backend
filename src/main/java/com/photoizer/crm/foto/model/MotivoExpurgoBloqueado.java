package com.photoizer.crm.foto.model;

/**
 * Motivo pelo qual uma galeria não pode ser expurgada agora.
 * Exposto na API para o frontend explicar o bloqueio sem duplicar regras.
 */
public enum MotivoExpurgoBloqueado {
    NAO_FINALIZADO,
    JA_ARQUIVADA,
    PENDENCIA,
    SEM_FOTOS
}
