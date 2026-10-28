package com.photoizer.crm.shared.processing;

/**
 * Presets de qualidade da prévia pública da galeria.
 *
 * <p>A prévia é sempre protegida por marca d'água. O arquivo de download do
 * cliente continua sendo o original/editado, nunca o preview.
 *
 * <p>{@link #ORIGINAL} mantém o prefixo {@code preview_v5_} para reaproveitar
 * as prévias full-res geradas antes da configuração por galeria. Os demais
 * presets usam o prefixo {@code preview_v6_}.
 */
public enum QualidadePreview {

    PROTEGIDA("preview_v6_protegida_", 800, 0.60f),
    EQUILIBRADA("preview_v6_equilibrada_", 1600, 0.90f),
    ORIGINAL("preview_v5_", 0, 0.92f);

    private final String prefixoCache;
    private final int maxDim;
    private final float qualidade;

    QualidadePreview(String prefixoCache, int maxDim, float qualidade) {
        this.prefixoCache = prefixoCache;
        this.maxDim = maxDim;
        this.qualidade = qualidade;
    }

    /** Qualidade usada quando a galeria ainda não tem configuração explícita. */
    public static QualidadePreview padrao() {
        return ORIGINAL;
    }

    public String getPrefixoCache() {
        return prefixoCache;
    }

    public int getMaxDim() {
        return maxDim;
    }

    public float getQualidade() {
        return qualidade;
    }
}
