package com.photoizer.crm.shared.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * PATTERN: Facade Pattern
 *
 * Motivo: Centraliza a geração de PDF em um único serviço compartilhado.
 * A implementação atual usa OpenPDF (biblioteca leve e robusta) para gerar
 * PDFs com suporte completo a UTF-8, acentuação e layout flexível.
 *
 * Antes: Geração manual de bytes PDF 1.4 com encoding WinAnsi limitado (490+ linhas).
 * Agora: Delega para OpenPDF, que lida com complexidade de formato PDF,
 * encoding Unicode, fontes e layout (~50 linhas).
 *
 * Todos os módulos que precisam gerar PDF (documento, contrato) delegam
 * para esta classe, escondendo a complexidade de geração de PDF.
 *
 * Evolução:
 * - v1: Implementação manual (frágil, sem suporte a acentos)
 * - v2: OpenPDF (código limpo, robusto, extensível)
 */
@Component
public class PdfWriter {

    private static final Logger log = LoggerFactory.getLogger(PdfWriter.class);

    /**
     * Gera um PDF com o título e linhas fornecidos.
     *
     * @param titulo título do documento (aparece em negrito no topo)
     * @param linhas conteúdo do documento (uma linha por item da lista)
     * @return array de bytes do PDF gerado
     */
    public byte[] gerar(String titulo, List<String> linhas) {
        try (var baos = new ByteArrayOutputStream()) {
            var document = new Document(PageSize.A4, 50, 50, 50, 50);
            var writer = com.lowagie.text.pdf.PdfWriter.getInstance(document, baos);

            document.open();

            var fontTitulo = new Font(Font.HELVETICA, 14, Font.BOLD);
            document.add(new Paragraph(titulo, fontTitulo));
            document.add(new Paragraph(""));

            var fontConteudo = new Font(Font.HELVETICA, 10);
            for (String linha : linhas) {
                document.add(new Paragraph(linha != null ? linha : "", fontConteudo));
            }

            document.close();
            writer.close();

            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Erro ao gerar PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao gerar PDF", e);
        }
    }
}
