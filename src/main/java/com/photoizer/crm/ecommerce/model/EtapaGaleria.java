package com.photoizer.crm.ecommerce.model;

/**
 * Etapa operacional da galeria, derivada dos fatos do ensaio (status do
 * agendamento, fotos, selecao e fase da galeria).
 *
 * <p>Nao e persistida de proposito: gravar a etapa criaria uma segunda fonte de
 * verdade que pode divergir do fluxo real. A Central de Galerias calcula a
 * etapa sob demanda.
 *
 * <p>A prioridade define a ordem da fila de trabalho (menor = mais urgente).
 */
public enum EtapaGaleria {

    AGUARDANDO_UPLOAD(10, "Aguardando envio de fotos"),
    FOTOS_NAO_PUBLICADAS(20, "Fotos aguardando publicação"),
    COMPRA_EXTRA_PENDENTE(25, "Compra de extra aguardando aprovação"),
    SELECAO_EM_EDICAO(30, "Seleção em edição"),
    SELECAO_AGUARDANDO_APROVACAO(35, "Seleção aguardando aprovação"),
    AGUARDANDO_SELECAO(40, "Aguardando escolha do cliente"),
    AGUARDANDO_DOWNLOAD(50, "Publicada, aguardando download"),
    CLIENTE_BAIXOU(55, "Cliente baixou as fotos"),
    CONCLUIDA(60, "Concluída"),
    ARQUIVADA(70, "Arquivada");

    private final int prioridade;
    private final String label;

    EtapaGaleria(int prioridade, String label) {
        this.prioridade = prioridade;
        this.label = label;
    }

    public int getPrioridade() {
        return prioridade;
    }

    public String getLabel() {
        return label;
    }

    public boolean isTerminal() {
        return this == CONCLUIDA || this == ARQUIVADA || this == CLIENTE_BAIXOU;
    }
}
