package com.photoizer.crm.shared.pdf;

import java.util.List;

/**
 * Blocos de conteudo que compoem um {@link PdfDocument}.
 *
 * <p>O modelo mantem o conteudo independente do desenho: o parser converte o
 * texto do template nesses blocos e o {@link PdfRenderer} decide a aparencia.
 * Assim o layout pode evoluir sem tocar no texto do contrato.
 */
public sealed interface PdfBlock {

    /** Espaco vertical reservado entre blocos. */
    record Spacer(float height) implements PdfBlock {
    }

    /** Linha horizontal fina de separacao. */
    record Divider() implements PdfBlock {
    }

    /** Titulo de secao numerado (ex.: "4" + "Valores"). */
    record SectionTitle(String number, String text) implements PdfBlock {
    }

    /** Rotulo curto em destaque, sem numeracao. */
    record Subheading(String text) implements PdfBlock {
    }

    /** Paragrafo de corpo de texto. */
    record Paragraph(String text) implements PdfBlock {
    }

    /** Lista de itens com marcador. */
    record Bullets(List<String> items) implements PdfBlock {
    }

    /** Conjunto de pares rotulo/valor alinhados em duas colunas. */
    record KeyValues(List<Entry> entries) implements PdfBlock {
    }

    /** Destaque de um dado importante (ex.: dados para PIX). */
    record Callout(String label, String value, String note) implements PdfBlock {
    }

    /** Cartoes informativos, exibidos em duas colunas. */
    record CardGrid(List<Card> cards) implements PdfBlock {
    }

    /** Area de assinatura das partes. */
    record SignatureBlock(List<Signature> signatures) implements PdfBlock {
    }

    /** Caixa final de autenticacao (data, IP, hash). */
    record MetaBox(String title, List<Entry> entries) implements PdfBlock {
    }

    record Entry(String label, String value, boolean highlight) {
        public Entry(String label, String value) {
            this(label, value, false);
        }
    }

    record Card(String eyebrow, List<Entry> rows) {
    }

    record Signature(String role, String name, String document) {
    }
}
