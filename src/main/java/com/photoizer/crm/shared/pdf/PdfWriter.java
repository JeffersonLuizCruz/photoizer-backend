package com.photoizer.crm.shared.pdf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * PATTERN: Facade
 *
 * Ponto unico de geracao de PDF do sistema. Esconde do dominio o motor de
 * layout ({@link PdfRenderer}) e a conversao de texto em blocos
 * ({@link PdfMarkupParser}).
 *
 * <p>Dois pontos de entrada:
 * <ul>
 *   <li>{@link #gerar(PdfDocument)} — documentos com layout completo
 *       (termo de prestacao de servicos);</li>
 *   <li>{@link #gerar(String, List)} — adapter retrocompativel para conteudo
 *       textual simples (recibo), que tambem recebe o tema visual.</li>
 * </ul>
 */
@Component
public class PdfWriter {

    private static final Logger log = LoggerFactory.getLogger(PdfWriter.class);

    private final PdfRenderer renderer = new PdfRenderer();

    /**
     * Gera um PDF simples a partir de um titulo e linhas de texto.
     * Mantido para consumidores que ainda trabalham com conteudo textual
     * (ex.: recibo). As linhas passam pelo parser de marcacao, ganhando
     * hierarquia, listas e pares rotulo/valor automaticamente.
     */
    public byte[] gerar(String titulo, List<String> linhas) {
        var texto = linhas == null ? "" : String.join("\n", linhas);
        return gerar(PdfDocument.simple(titulo, PdfMarkupParser.parse(texto)));
    }

    /**
     * Gera um PDF a partir de um documento ja estruturado em blocos.
     */
    public byte[] gerar(PdfDocument documento) {
        try {
            return renderer.render(documento);
        } catch (RuntimeException e) {
            log.error("Erro ao gerar PDF '{}': {}", documento != null ? documento.title() : null,
                e.getMessage(), e);
            throw e;
        }
    }
}
