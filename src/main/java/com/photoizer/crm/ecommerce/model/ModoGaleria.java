package com.photoizer.crm.ecommerce.model;

/**
 * Modo de operacao da galeria.
 *
 * <ul>
 *   <li>{@link #DIRETO}: fluxo legado desativado. Mantido apenas para leitura de
 *       galerias antigas que ainda nao foram migradas. Nao usa snapshot de
 *       selecao.</li>
 *   <li>{@link #COM_EDICAO}: cliente envia selecao, o estudio faz uma edicao
 *       EXTRA depois da selecao e publica a versao editada antes da
 *       comercializacao.</li>
 *   <li>{@link #ENTREGA_DIRETA}: cliente envia selecao e o staff apenas a
 *       aprova. NAO existe etapa de edicao depois da selecao: o cliente recebe
 *       o arquivo que ja esta publicado na galeria. Esse arquivo pode ter sido
 *       editado antes do upload; portanto, "original" aqui NAO significa "foto
 *       nao editada". Extras continuam sendo liberados somente apos a
 *       confirmacao do pagamento.</li>
 * </ul>
 *
 * <p>Prefira os predicados abaixo a comparacoes diretas com o enum: eles
 * concentram as diferencas de comportamento entre os modos.
 */
public enum ModoGaleria {
    DIRETO,
    COM_EDICAO,
    ENTREGA_DIRETA;

    /** Indica se o modo usa o snapshot imutavel da selecao enviada pelo cliente. */
    public boolean usaSelecaoSnapshot() {
        return this != DIRETO;
    }

    /** Indica se o staff precisa enviar/publicar versoes editadas. */
    public boolean requerEdicao() {
        return this == COM_EDICAO;
    }

    /**
     * Indica se o entregavel do pacote e o arquivo ja publicado na galeria,
     * sem uma etapa de edicao posterior a selecao.
     *
     * <p>O arquivo publicado pode ter sido editado antes do upload; o nome do
     * metodo evita a interpretacao equivocada de "original = nao editado".
     */
    public boolean entregaArquivoPublicado() {
        return this == ENTREGA_DIRETA;
    }

    /** Modos aceitos para novas galerias (DIRETO permanece apenas como legado). */
    public boolean disponivelParaNovasGalerias() {
        return this != DIRETO;
    }
}
