package com.photoizer.crm.shared.pdf;

import java.util.List;

/**
 * Documento pronto para renderizacao: capa (hero), faixa de fatos, blocos de
 * conteudo e rodape. Mantem apenas dados ja formatados, sem entidades de
 * dominio, para que o renderer seja puro e testavel.
 */
public record PdfDocument(
        String title,
        String author,
        String subject,
        Hero hero,
        List<Fact> facts,
        List<PdfBlock> blocks,
        String footerLeft,
        String footerCenter) {

    public PdfDocument {
        facts = facts != null ? List.copyOf(facts) : List.of();
        blocks = blocks != null ? List.copyOf(blocks) : List.of();
    }

    /**
     * Documento simples (usado pelo adapter legado, ex.: recibo): hero com o
     * titulo informado e sem dados corporativos.
     */
    public static PdfDocument simple(String titulo, List<PdfBlock> blocks) {
        return new PdfDocument(
            titulo,
            null,
            null,
            new Hero("Documento", null, titulo, null, null),
            List.of(),
            blocks,
            null,
            null);
    }

    /** Faixa superior do documento (somente pagina 1). */
    public record Hero(String eyebrow, String monogram, String brand, String status, String meta) {
    }

    /** Par rotulo/valor exibido na faixa de fatos abaixo do hero. */
    public record Fact(String label, String value) {
    }
}
