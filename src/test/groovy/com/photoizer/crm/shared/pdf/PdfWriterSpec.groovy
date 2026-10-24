package com.photoizer.crm.shared.pdf

import com.lowagie.text.pdf.PdfReader
import com.lowagie.text.pdf.parser.PdfTextExtractor
import spock.lang.Specification

class PdfWriterSpec extends Specification {

    def writer = new PdfWriter()

    def "gera PDF válido com acentuação e conteúdo simples"() {
        when: "um documento simples é gerado (adapter do recibo)"
        def bytes = writer.gerar("RECIBO DE PAGAMENTO", [
            "Recibo de pagamento referente ao agendamento:",
            "",
            "Cliente: Maria Souza",
            "Pacote: Ensaio Casal",
            "Valor total: R\$ 1.800,00",
            "Valor restante: R\$ 1.260,00"
        ])

        then: "o arquivo é um PDF legível"
        bytes.length > 0
        new String(bytes, 0, 5, "US-ASCII") == "%PDF-"

        and: "o conteúdo e os acentos são preservados"
        def texto = textoDe(bytes).toLowerCase()
        texto.contains("recibo de pagamento")
        texto.contains("maria souza")
        texto.contains("ensaio casal")
        texto.contains("1.800,00")
    }

    def "pagina conteúdo longo e numera as páginas"() {
        given: "um documento com muitas cláusulas"
        def linhas = (1..90).collect { "Cláusula ${it} de referência com acentuação e conteúdo suficiente para ocupar a linha inteira do contrato." }

        when: "o documento é gerado"
        def bytes = writer.gerar("CONTRATO LONGO", linhas)

        then: "o conteúdo ocupa mais de uma página"
        def reader = new PdfReader(bytes)
        reader.numberOfPages > 1
        reader.close()

        and: "o rodapé informa o total de páginas"
        def texto = textoDe(bytes)
        texto.contains("Página 1 de ")
        texto.contains("Cláusula 1 de referência")
        texto.contains("Cláusula 90 de referência")
    }

    def "gera documento estruturado com capa, fatos e blocos"() {
        given: "um documento com todos os tipos de bloco"
        def documento = new PdfDocument(
            "Termo de Prestação de Serviços Fotográficos",
            "Carol Oliva Fotografia",
            "Cliente Mariana Alves",
            new PdfDocument.Hero(
                "Termo de prestação de serviços fotográficos",
                "CO",
                "Carol Oliva Fotografia",
                "Assinado eletronicamente",
                "Proposta nº 8F3A2C1D · 24/10/2026"),
            List.of(
                new PdfDocument.Fact("Data do ensaio", "24/10/2026"),
                new PdfDocument.Fact("Local", "Praia do Rosa"),
                new PdfDocument.Fact("Pacote", "Ensaio Casal")),
            List.of(
                new PdfBlock.CardGrid(List.of(
                    new PdfBlock.Card("Cliente", List.of(new PdfBlock.Entry("Nome", "Mariana Alves"))),
                    new PdfBlock.Card("Valores", List.of(new PdfBlock.Entry("Reserva", "R\$ 540,00", true))))),
                new PdfBlock.Callout("Dados para pagamento via PIX", "CNPJ · 00.000.000/0001-00", null),
                new PdfBlock.Divider(),
                new PdfBlock.SectionTitle("1", "Dados do Cliente"),
                new PdfBlock.KeyValues(List.of(new PdfBlock.Entry("Nome completo", "Mariana Alves"))),
                new PdfBlock.Bullets(List.of("Fotos em alta resolução", "Galeria digital")),
                new PdfBlock.SignatureBlock(List.of(
                    new PdfBlock.Signature("Contratante", "Mariana Alves", "CPF 000.000.000-00"))),
                new PdfBlock.MetaBox("Autenticação do documento", List.of(
                    new PdfBlock.Entry("Assinado em", "24/10/2026 às 15:42")))),
            "Carol Oliva Fotografia · CNPJ 00.000.000/0001-00",
            null)

        when: "o documento é renderizado"
        def bytes = writer.gerar(documento)

        then: "capa, resumo e metadados aparecem no PDF"
        def texto = textoDe(bytes).toLowerCase()
        texto.contains("carol oliva fotografia")
        texto.contains("mariana alves")
        texto.contains("praia do rosa")
        texto.contains("dados do cliente")
        texto.contains("autenticação do documento")
        texto.contains("página 1 de")
    }

    private static String textoDe(byte[] bytes) {
        def reader = new PdfReader(bytes)
        try {
            def extractor = new PdfTextExtractor(reader)
            return (1..reader.numberOfPages).collect { extractor.getTextFromPage(it) }.join("\n")
        } finally {
            reader.close()
        }
    }
}
