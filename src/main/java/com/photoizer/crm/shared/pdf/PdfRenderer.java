package com.photoizer.crm.shared.pdf;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Locale;

/**
 * Renderiza um {@link PdfDocument} em um PDF A4 com identidade visual.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>capa (hero) com marca e status do documento;</li>
 *   <li>faixa de fatos (data, local, pacote);</li>
 *   <li>blocos de conteudo com hierarquia tipografica;</li>
 *   <li>cabecalho compacto a partir da pagina 2;</li>
 *   <li>rodape com "Pagina X de Y" (duas passadas para conhecer o total).</li>
 * </ul>
 *
 * <p>O renderer nao depende de Spring nem de entidades de dominio: recebe um
 * documento ja formatado e devolve os bytes. Isso o torna facil de testar e
 * reutilizavel por contrato e recibo.
 */
public class PdfRenderer {

    private static final String CREATOR = "Photoizer CRM";

    public byte[] render(PdfDocument documento) {
        var primeiraPassada = renderInternal(documento, 0);
        int paginas;
        try {
            var reader = new com.lowagie.text.pdf.PdfReader(primeiraPassada);
            try {
                paginas = reader.getNumberOfPages();
            } finally {
                reader.close();
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Erro ao contar páginas do PDF", e);
        }
        return renderInternal(documento, paginas);
    }

    private byte[] renderInternal(PdfDocument documento, int totalPaginas) {
        var out = new ByteArrayOutputStream();
        var document = new Document(PageSize.A4,
            PdfTheme.MARGIN_LEFT, PdfTheme.MARGIN_RIGHT,
            PdfTheme.MARGIN_TOP, PdfTheme.MARGIN_BOTTOM);

        try {
            var writer = com.lowagie.text.pdf.PdfWriter.getInstance(document, out);
            writer.setPageEvent(new PageDecorator(documento, totalPaginas));

            preencherMetadados(document, documento);

            document.open();
            addHero(document, documento);
            addFacts(document, documento);

            for (var bloco : documento.blocks()) {
                addBlock(document, writer, bloco);
            }

            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Erro ao gerar PDF", e);
        }
    }

    private static void preencherMetadados(Document document, PdfDocument documento) {
        if (documento.title() != null && !documento.title().isBlank()) {
            document.addTitle(documento.title());
        }
        if (documento.author() != null && !documento.author().isBlank()) {
            document.addAuthor(documento.author());
        }
        if (documento.subject() != null && !documento.subject().isBlank()) {
            document.addSubject(documento.subject());
        }
        document.addCreator(CREATOR);
        document.addCreationDate();
    }

    // ── Capa ────────────────────────────────────────────────────────────

    private void addHero(Document document, PdfDocument documento) throws DocumentException {
        var hero = documento.hero();
        if (hero == null) {
            return;
        }

        var tabela = new PdfPTable(1);
        tabela.setWidthPercentage(100);
        tabela.setSpacingAfter(0);

        var celula = new PdfPCell();
        celula.setBackgroundColor(PdfTheme.ACCENT);
        celula.setBorder(Rectangle.NO_BORDER);
        celula.setPadding(18f);
        celula.setPaddingBottom(16f);

        var topo = new PdfPTable(new float[]{70f, 30f});
        topo.setWidthPercentage(100);

        var esquerda = new PdfPCell();
        esquerda.setBorder(Rectangle.NO_BORDER);
        esquerda.setPadding(0);
        esquerda.setPaddingRight(8f);
        esquerda.addElement(eyebrow(hero.eyebrow(), PdfTheme.WHITE_SOFT));
        if (hero.monogram() != null && !hero.monogram().isBlank()) {
            esquerda.addElement(brandRow(hero.monogram(), hero.brand()));
        } else {
            esquerda.addElement(new Paragraph(hero.brand(), PdfTheme.serif(23f, PdfTheme.WHITE)));
        }
        if (hero.meta() != null && !hero.meta().isBlank()) {
            var meta = new Paragraph(hero.meta(), PdfTheme.sans(8f, Font.NORMAL, PdfTheme.WHITE_SOFT));
            meta.setSpacingBefore(5f);
            esquerda.addElement(meta);
        }

        var direita = new PdfPCell();
        direita.setBorder(Rectangle.NO_BORDER);
        direita.setPadding(0);
        direita.setVerticalAlignment(Element.ALIGN_TOP);
        if (hero.status() != null && !hero.status().isBlank()) {
            direita.addElement(statusChip(hero.status()));
        }

        topo.addCell(esquerda);
        topo.addCell(direita);
        celula.addElement(topo);

        tabela.addCell(celula);
        document.add(tabela);
    }

    private PdfPTable brandRow(String monograma, String marca) {
        var tabela = new PdfPTable(new float[]{34f, 240f});
        tabela.setWidthPercentage(100);
        tabela.setSpacingBefore(4f);

        var selo = new PdfPCell(new Phrase(monograma, PdfTheme.serif(15f, PdfTheme.WHITE)));
        selo.setBackgroundColor(PdfTheme.ACCENT_DEEP);
        selo.setBorder(Rectangle.NO_BORDER);
        selo.setFixedHeight(34f);
        selo.setPadding(0);
        selo.setHorizontalAlignment(Element.ALIGN_CENTER);
        selo.setVerticalAlignment(Element.ALIGN_MIDDLE);

        var nome = new PdfPCell(new Phrase(marca, PdfTheme.serif(22f, PdfTheme.WHITE)));
        nome.setBorder(Rectangle.NO_BORDER);
        nome.setPadding(0);
        nome.setPaddingLeft(10f);
        nome.setVerticalAlignment(Element.ALIGN_MIDDLE);

        tabela.addCell(selo);
        tabela.addCell(nome);
        return tabela;
    }

    private PdfPTable statusChip(String status) {
        var tabela = new PdfPTable(1);
        tabela.setWidthPercentage(100f);
        tabela.setHorizontalAlignment(Element.ALIGN_RIGHT);

        var celula = new PdfPCell(new Phrase(status.toUpperCase(Locale.ROOT),
            PdfTheme.sans(6.8f, Font.BOLD, PdfTheme.SUCCESS)));
        celula.setBackgroundColor(PdfTheme.SUCCESS_SOFT);
        celula.setBorder(Rectangle.NO_BORDER);
        celula.setPadding(5f);
        celula.setPaddingLeft(8f);
        celula.setPaddingRight(8f);
        celula.setHorizontalAlignment(Element.ALIGN_CENTER);

        tabela.addCell(celula);
        return tabela;
    }

    // ── Faixa de fatos ──────────────────────────────────────────────────

    private void addFacts(Document document, PdfDocument documento) throws DocumentException {
        var fatos = documento.facts();
        if (fatos.isEmpty()) {
            return;
        }

        var tabela = new PdfPTable(fatos.size());
        tabela.setWidthPercentage(100);
        tabela.setSpacingBefore(0f);
        tabela.setSpacingAfter(12f);

        for (var fato : fatos) {
            var celula = new PdfPCell();
            celula.setBorder(Rectangle.BOTTOM);
            celula.setBorderColor(PdfTheme.RULE);
            celula.setBorderWidth(0.7f);
            celula.setPaddingTop(12f);
            celula.setPaddingBottom(10f);
            celula.setPaddingRight(10f);
            celula.addElement(eyebrow(fato.label(), PdfTheme.ACCENT));
            celula.addElement(new Paragraph(fato.value(), PdfTheme.sans(10f, Font.BOLD, PdfTheme.INK)));
            tabela.addCell(celula);
        }

        document.add(tabela);
    }

    // ── Blocos ──────────────────────────────────────────────────────────

    private void addBlock(Document document, com.lowagie.text.pdf.PdfWriter writer, PdfBlock bloco)
            throws DocumentException {
        switch (bloco) {
            case PdfBlock.Spacer spacer -> document.add(spacer(spacer.height()));
            case PdfBlock.Divider ignored -> document.add(divider());
            case PdfBlock.SectionTitle titulo -> {
                reservarEspaco(document, writer, 90f);
                document.add(sectionTitle(titulo.number(), titulo.text()));
            }
            case PdfBlock.Subheading subtitulo -> {
                reservarEspaco(document, writer, 64f);
                document.add(subheading(subtitulo.text()));
            }
            case PdfBlock.Paragraph paragrafo -> document.add(paragraph(paragrafo.text()));
            case PdfBlock.Bullets bullets -> document.add(bullets(bullets.items()));
            case PdfBlock.KeyValues pares -> document.add(keyValues(pares.entries()));
            case PdfBlock.Callout callout -> document.add(callout(callout));
            case PdfBlock.CardGrid cartoes -> {
                reservarEspaco(document, writer, 130f);
                addCardGrid(document, cartoes.cards());
            }
            case PdfBlock.SignatureBlock assinaturas -> {
                reservarEspaco(document, writer, 150f);
                document.add(signatureBlock(assinaturas.signatures()));
            }
            case PdfBlock.MetaBox meta -> {
                reservarEspaco(document, writer, 110f);
                document.add(metaBox(meta));
            }
        }
    }

    private void reservarEspaco(Document document, com.lowagie.text.pdf.PdfWriter writer, float altura) {
        float y = writer.getVerticalPosition(true);
        if (y - altura < document.bottomMargin() + 6f) {
            document.newPage();
        }
    }

    private PdfPTable sectionTitle(String numero, String texto) {
        var tabela = new PdfPTable(new float[]{3f, 97f});
        tabela.setWidthPercentage(100);
        tabela.setSpacingBefore(16f);
        tabela.setSpacingAfter(8f);
        tabela.setKeepTogether(true);

        var barra = new PdfPCell(new Phrase(" "));
        barra.setBackgroundColor(PdfTheme.ACCENT);
        barra.setBorder(Rectangle.NO_BORDER);
        barra.setPadding(0);
        barra.setFixedHeight(16f);

        var titulo = new Paragraph();
        titulo.setLeading(16f);
        if (numero != null && !numero.isBlank()) {
            var n = new Chunk(numero, PdfTheme.sans(9f, Font.BOLD, PdfTheme.ACCENT));
            n.setCharacterSpacing(0.4f);
            titulo.add(n);
            titulo.add(new Chunk("   ", PdfTheme.sans(9f, Font.NORMAL, PdfTheme.ACCENT)));
        }
        titulo.add(new Chunk(texto, PdfTheme.serif(13.5f, PdfTheme.INK)));

        var celula = new PdfPCell(titulo);
        celula.setBorder(Rectangle.NO_BORDER);
        celula.setPadding(0);
        celula.setPaddingLeft(8f);
        celula.setVerticalAlignment(Element.ALIGN_MIDDLE);

        tabela.addCell(barra);
        tabela.addCell(celula);
        return tabela;
    }

    private Paragraph subheading(String texto) {
        var paragrafo = new Paragraph(texto, PdfTheme.sans(9.5f, Font.BOLD, PdfTheme.INK));
        paragrafo.setSpacingBefore(8f);
        paragrafo.setSpacingAfter(4f);
        return paragrafo;
    }

    private Paragraph paragraph(String texto) {
        var paragrafo = new Paragraph(texto, PdfTheme.body());
        paragrafo.setLeading(14.5f);
        paragrafo.setAlignment(Element.ALIGN_LEFT);
        paragrafo.setSpacingAfter(5f);
        return paragrafo;
    }

    private PdfPTable bullets(List<String> itens) {
        var tabela = new PdfPTable(new float[]{3f, 97f});
        tabela.setWidthPercentage(100);
        tabela.setSpacingBefore(2f);
        tabela.setSpacingAfter(6f);

        for (var item : itens) {
            var marcador = new Paragraph("•", PdfTheme.sans(10f, Font.BOLD, PdfTheme.ACCENT_BRIGHT));
            marcador.setLeading(14.5f);
            var celulaMarcador = new PdfPCell(marcador);
            celulaMarcador.setBorder(Rectangle.NO_BORDER);
            celulaMarcador.setPadding(0);
            celulaMarcador.setPaddingTop(1f);
            celulaMarcador.setVerticalAlignment(Element.ALIGN_TOP);

            var texto = paragraph(item);
            texto.setSpacingAfter(2f);
            var celulaTexto = new PdfPCell(texto);
            celulaTexto.setBorder(Rectangle.NO_BORDER);
            celulaTexto.setPadding(0);
            celulaTexto.setPaddingLeft(4f);
            celulaTexto.setPaddingTop(1f);
            celulaTexto.setVerticalAlignment(Element.ALIGN_TOP);

            tabela.addCell(celulaMarcador);
            tabela.addCell(celulaTexto);
        }
        return tabela;
    }

    private PdfPTable keyValues(List<PdfBlock.Entry> pares) {
        var tabela = new PdfPTable(new float[]{34f, 66f});
        tabela.setWidthPercentage(100);
        tabela.setSpacingBefore(4f);
        tabela.setSpacingAfter(6f);

        for (var par : pares) {
            var rotulo = new PdfPCell(new Phrase(par.label(),
                PdfTheme.sans(8.5f, par.highlight() ? Font.BOLD : Font.NORMAL,
                    par.highlight() ? PdfTheme.WARM_INK : PdfTheme.MUTED)));
            rotulo.setBorder(Rectangle.NO_BORDER);
            rotulo.setPaddingTop(1.5f);
            rotulo.setPaddingBottom(4.5f);
            rotulo.setPaddingRight(8f);
            rotulo.setPaddingLeft(par.highlight() ? 6f : 0f);
            if (par.highlight()) {
                rotulo.setBackgroundColor(PdfTheme.WARM_SOFT);
            }

            var corValor = par.highlight() ? PdfTheme.WARM_INK : PdfTheme.INK;
            var valor = new PdfPCell(new Phrase(par.value(),
                PdfTheme.sans(9.5f, par.highlight() ? Font.BOLD : Font.NORMAL, corValor)));
            valor.setBorder(Rectangle.NO_BORDER);
            valor.setPaddingTop(1.5f);
            valor.setPaddingBottom(4.5f);
            if (par.highlight()) {
                valor.setBackgroundColor(PdfTheme.WARM_SOFT);
                valor.setPaddingRight(6f);
            }

            tabela.addCell(rotulo);
            tabela.addCell(valor);
        }
        return tabela;
    }

    private PdfPTable callout(PdfBlock.Callout callout) {
        var tabela = new PdfPTable(1);
        tabela.setWidthPercentage(100);
        tabela.setSpacingBefore(6f);
        tabela.setSpacingAfter(10f);
        tabela.setKeepTogether(true);

        var celula = new PdfPCell();
        celula.setBackgroundColor(PdfTheme.ACCENT_SOFT);
        celula.setBorder(Rectangle.LEFT);
        celula.setBorderColor(PdfTheme.ACCENT);
        celula.setBorderWidthLeft(2.5f);
        celula.setPadding(10f);
        celula.setPaddingLeft(12f);
        celula.addElement(eyebrow(callout.label(), PdfTheme.ACCENT));

        var valor = new Paragraph(callout.value(), PdfTheme.sans(10f, Font.BOLD, PdfTheme.INK));
        valor.setSpacingBefore(3f);
        celula.addElement(valor);

        if (callout.note() != null && !callout.note().isBlank()) {
            var nota = new Paragraph(callout.note(), PdfTheme.sans(8.5f, Font.NORMAL, PdfTheme.BODY));
            nota.setSpacingBefore(2f);
            celula.addElement(nota);
        }

        tabela.addCell(celula);
        return tabela;
    }

    private void addCardGrid(Document document, List<PdfBlock.Card> cartoes) throws DocumentException {
        for (int i = 0; i < cartoes.size(); i += 2) {
            var temSegundo = i + 1 < cartoes.size();
            var tabela = new PdfPTable(temSegundo ? new float[]{49f, 2f, 49f} : new float[]{100f});
            tabela.setWidthPercentage(100);
            tabela.setSpacingAfter(8f);
            tabela.setKeepTogether(true);

            tabela.addCell(cardCell(cartoes.get(i)));
            if (temSegundo) {
                var espaco = new PdfPCell(new Phrase(" "));
                espaco.setBorder(Rectangle.NO_BORDER);
                espaco.setPadding(0);
                tabela.addCell(espaco);
                tabela.addCell(cardCell(cartoes.get(i + 1)));
            }
            document.add(tabela);
        }
    }

    private PdfPCell cardCell(PdfBlock.Card cartao) {
        var celula = new PdfPCell();
        celula.setBackgroundColor(PdfTheme.SURFACE);
        celula.setBorder(Rectangle.BOX);
        celula.setBorderColor(PdfTheme.RULE);
        celula.setBorderWidth(0.7f);
        celula.setPadding(11f);
        celula.setPaddingBottom(8f);
        celula.addElement(eyebrow(cartao.eyebrow(), PdfTheme.ACCENT));

        var linhas = new PdfPTable(new float[]{46f, 54f});
        linhas.setWidthPercentage(100);
        linhas.setSpacingBefore(4f);
        for (var linha : cartao.rows()) {
            var rotulo = new PdfPCell(new Phrase(linha.label(),
                PdfTheme.sans(7.5f, linha.highlight() ? Font.BOLD : Font.NORMAL,
                    linha.highlight() ? PdfTheme.WARM_INK : PdfTheme.MUTED)));
            rotulo.setBorder(Rectangle.NO_BORDER);
            rotulo.setPaddingTop(1f);
            rotulo.setPaddingBottom(3.5f);
            rotulo.setPaddingRight(5f);
            rotulo.setPaddingLeft(linha.highlight() ? 5f : 0f);
            if (linha.highlight()) {
                rotulo.setBackgroundColor(PdfTheme.WARM_SOFT);
            }

            var corValor = linha.highlight() ? PdfTheme.WARM_INK : PdfTheme.INK;
            var valor = new PdfPCell(new Phrase(linha.value(),
                PdfTheme.sans(8.8f, linha.highlight() ? Font.BOLD : Font.NORMAL, corValor)));
            valor.setBorder(Rectangle.NO_BORDER);
            valor.setPaddingTop(1f);
            valor.setPaddingBottom(3.5f);
            if (linha.highlight()) {
                valor.setBackgroundColor(PdfTheme.WARM_SOFT);
                valor.setPaddingRight(5f);
            }

            linhas.addCell(rotulo);
            linhas.addCell(valor);
        }
        celula.addElement(linhas);
        return celula;
    }

    private PdfPTable signatureBlock(List<PdfBlock.Signature> assinaturas) {
        var tabela = new PdfPTable(Math.max(assinaturas.size(), 1));
        tabela.setWidthPercentage(100);
        tabela.setSpacingBefore(18f);
        tabela.setSpacingAfter(12f);
        tabela.setKeepTogether(true);

        for (var assinatura : assinaturas) {
            var celula = new PdfPCell();
            celula.setBorder(Rectangle.NO_BORDER);
            celula.setPadding(0);
            celula.setPaddingRight(16f);
            celula.addElement(eyebrow(assinatura.role(), PdfTheme.MUTED));

            var linha = new PdfPTable(1);
            linha.setWidthPercentage(100);
            linha.setSpacingBefore(14f);
            var traco = new PdfPCell(new Phrase(" "));
            traco.setBorder(Rectangle.BOTTOM);
            traco.setBorderColor(new Color(0x94A3B8));
            traco.setBorderWidth(0.9f);
            traco.setFixedHeight(18f);
            traco.setPadding(0);
            linha.addCell(traco);
            celula.addElement(linha);

            var nome = new Paragraph(assinatura.name(), PdfTheme.sans(9.5f, Font.BOLD, PdfTheme.INK));
            nome.setSpacingBefore(5f);
            celula.addElement(nome);

            if (assinatura.document() != null && !assinatura.document().isBlank()) {
                celula.addElement(new Paragraph(assinatura.document(), PdfTheme.sans(8f, Font.NORMAL, PdfTheme.MUTED)));
            }
            tabela.addCell(celula);
        }
        return tabela;
    }

    private PdfPTable metaBox(PdfBlock.MetaBox meta) {
        var tabela = new PdfPTable(1);
        tabela.setWidthPercentage(100);
        tabela.setSpacingBefore(16f);
        tabela.setSpacingAfter(4f);
        tabela.setKeepTogether(true);

        var celula = new PdfPCell();
        celula.setBackgroundColor(PdfTheme.SURFACE);
        celula.setBorder(Rectangle.BOX);
        celula.setBorderColor(PdfTheme.RULE);
        celula.setBorderWidth(0.7f);
        celula.setPadding(11f);
        celula.addElement(eyebrow(meta.title(), PdfTheme.MUTED));

        var linhas = new PdfPTable(new float[]{30f, 70f});
        linhas.setWidthPercentage(100);
        linhas.setSpacingBefore(4f);
        for (var linha : meta.entries()) {
            var rotulo = new PdfPCell(new Phrase(linha.label(), PdfTheme.sans(7.5f, Font.NORMAL, PdfTheme.MUTED)));
            rotulo.setBorder(Rectangle.NO_BORDER);
            rotulo.setPaddingTop(1f);
            rotulo.setPaddingBottom(3f);

            var valor = new PdfPCell(new Phrase(linha.value(), PdfTheme.sans(8f, Font.NORMAL, PdfTheme.INK)));
            valor.setBorder(Rectangle.NO_BORDER);
            valor.setPaddingTop(1f);
            valor.setPaddingBottom(3f);

            linhas.addCell(rotulo);
            linhas.addCell(valor);
        }
        celula.addElement(linhas);
        tabela.addCell(celula);
        return tabela;
    }

    private PdfPTable spacer(float altura) {
        var tabela = new PdfPTable(1);
        tabela.setWidthPercentage(100);
        var celula = new PdfPCell(new Phrase(" "));
        celula.setBorder(Rectangle.NO_BORDER);
        celula.setFixedHeight(Math.max(altura, 1f));
        celula.setPadding(0);
        tabela.addCell(celula);
        return tabela;
    }

    private PdfPTable divider() {
        var tabela = new PdfPTable(1);
        tabela.setWidthPercentage(100);
        tabela.setSpacingBefore(6f);
        tabela.setSpacingAfter(10f);
        var celula = new PdfPCell(new Phrase(" "));
        celula.setBorder(Rectangle.BOTTOM);
        celula.setBorderColor(PdfTheme.RULE);
        celula.setBorderWidth(0.6f);
        celula.setFixedHeight(1f);
        celula.setPadding(0);
        tabela.addCell(celula);
        return tabela;
    }

    private static Paragraph eyebrow(String texto, Color cor) {
        var paragrafo = new Paragraph();
        paragrafo.setLeading(9f);
        if (texto == null || texto.isBlank()) {
            return paragrafo;
        }
        var chunk = new Chunk(texto.toUpperCase(Locale.ROOT), PdfTheme.sans(7f, Font.BOLD, cor));
        chunk.setCharacterSpacing(1.1f);
        paragrafo.add(chunk);
        return paragrafo;
    }

    /**
     * Desenha rodape em todas as paginas e um cabecalho compacto a partir da
     * segunda. O total de paginas vem da primeira passada.
     */
    private static final class PageDecorator extends PdfPageEventHelper {

        private final PdfDocument documento;
        private final int totalPaginas;

        private PageDecorator(PdfDocument documento, int totalPaginas) {
            this.documento = documento;
            this.totalPaginas = totalPaginas;
        }

        @Override
        public void onEndPage(com.lowagie.text.pdf.PdfWriter writer, Document document) {
            var cb = writer.getDirectContent();
            float esquerda = PdfTheme.MARGIN_LEFT;
            float direita = document.getPageSize().getWidth() - PdfTheme.MARGIN_RIGHT;
            float base = 30f;

            cb.saveState();
            cb.setColorStroke(PdfTheme.RULE);
            cb.setLineWidth(0.6f);
            cb.moveTo(esquerda, base + 12f);
            cb.lineTo(direita, base + 12f);
            cb.stroke();
            cb.restoreState();

            if (documento.footerLeft() != null && !documento.footerLeft().isBlank()) {
                ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                    new Phrase(documento.footerLeft(), PdfTheme.sans(7.5f, Font.NORMAL, PdfTheme.MUTED)),
                    esquerda, base, 0);
            }

            if (documento.footerCenter() != null && !documento.footerCenter().isBlank()) {
                ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                    new Phrase(documento.footerCenter(), PdfTheme.sans(7.5f, Font.NORMAL, PdfTheme.MUTED)),
                    (esquerda + direita) / 2f, base, 0);
            }

            var paginacao = "Página " + writer.getPageNumber()
                + (totalPaginas > 0 ? " de " + totalPaginas : "");
            ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                new Phrase(paginacao, PdfTheme.sans(7.5f, Font.NORMAL, PdfTheme.MUTED)),
                direita, base, 0);

            if (writer.getPageNumber() > 1) {
                float topo = document.getPageSize().getHeight() - PdfTheme.MARGIN_TOP + 24f;

                cb.saveState();
                cb.setColorStroke(PdfTheme.RULE);
                cb.setLineWidth(0.6f);
                cb.moveTo(esquerda, topo - 9f);
                cb.lineTo(direita, topo - 9f);
                cb.stroke();
                cb.restoreState();

                if (documento.hero() != null && documento.hero().brand() != null) {
                    ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                        new Phrase(documento.hero().brand(), PdfTheme.sans(8f, Font.BOLD, PdfTheme.ACCENT)),
                        esquerda, topo, 0);
                }
                if (documento.title() != null) {
                    ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                        new Phrase(documento.title(), PdfTheme.sans(7.5f, Font.NORMAL, PdfTheme.MUTED)),
                        direita, topo, 0);
                }
            }
        }
    }
}
