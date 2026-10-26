package com.photoizer.crm.ecommerce.model;

/**
 * Modo de operacao da galeria.
 *
 * <ul>
 *   <li>{@link #DIRETO}: fluxo legado desativado. Mantido apenas para leitura de
 *       galerias antigas que ainda nao foram migradas. Nao usa snapshot de
 *       selecao.</li>
 *   <li>{@link #COM_EDICAO}: cliente envia selecao, staff edita e publica a
 *       versao editada antes da comercializacao.</li>
 *   <li>{@link #SEM_EDICAO}: cliente envia selecao e o staff apenas a aprova;
 *       as fotos do pacote sao entregues em versao original. Extras continuam
 *       sendo liberados somente apos a confirmacao do pagamento.</li>
 * </ul>
 *
 * <p>Prefira os predicados abaixo a comparacoes diretas com o enum: eles
 * concentram as diferencas de comportamento entre os modos.
 */
public enum ModoGaleria {
    DIRETO,
    COM_EDICAO,
    SEM_EDICAO;

    /** Indica se o modo usa o snapshot imutavel da selecao enviada pelo cliente. */
    public boolean usaSelecaoSnapshot() {
        return this != DIRETO;
    }

    /** Indica se o staff precisa enviar/publicar versoes editadas. */
    public boolean requerEdicao() {
        return this == COM_EDICAO;
    }

    /** Indica se o entregavel do pacote e o proprio arquivo original. */
    public boolean entregaOriginal() {
        return this == SEM_EDICAO;
    }

    /** Modos aceitos para novas galerias (DIRETO permanece apenas como legado). */
    public boolean disponivelParaNovasGalerias() {
        return this != DIRETO;
    }
}
