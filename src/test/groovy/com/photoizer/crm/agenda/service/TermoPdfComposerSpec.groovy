package com.photoizer.crm.agenda.service

import com.lowagie.text.pdf.PdfReader
import com.lowagie.text.pdf.parser.PdfTextExtractor
import com.photoizer.crm.config.model.ConfigKey
import com.photoizer.crm.shared.pdf.PdfBlock
import com.photoizer.crm.shared.pdf.PdfWriter
import spock.lang.Specification
import spock.lang.Unroll

import java.time.LocalDateTime

import static com.photoizer.crm.agenda.support.AgendaFixtures.agendamento

class TermoPdfComposerSpec extends Specification {

    def composer = new TermoPdfComposer()
    def pdfWriter = new PdfWriter()

    def "monta o termo com capa, resumo, cláusulas, assinatura e autenticação"() {
        given: "um agendamento e o template padrão renderizado"
        def proposta = agendamento()
        def placeholders = placeholdersValidos()
        def texto = textoPadrao(placeholders)
        def dados = new TermoPdfComposer.DadosAssinatura(
            "Mariana Alves",
            LocalDateTime.of(2026, 10, 24, 15, 42),
            "203.0.113.7",
            "9f2c1a7b3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcd")

        when: "o documento é composto"
        def documento = composer.compor(proposta, placeholders, texto, dados)

        then: "a capa usa a marca e o status de assinatura"
        documento.hero().brand() == "Carol Oliva Fotografia"
        documento.hero().monogram() == "CO"
        documento.hero().status() == "Assinado eletronicamente"
        documento.hero().meta().startsWith("Proposta nº ")
        documento.hero().meta().contains("24/10/2026")

        and: "a faixa de fatos resume o ensaio"
        documento.facts()*.label == ["Data do ensaio", "Local", "Pacote"]
        documento.facts()*.value == ["24/10/2026", "Praia do Rosa", "Ensaio Casal"]

        and: "o resumo é composto por quatro cartões"
        def cartoes = documento.blocks().find { it instanceof PdfBlock.CardGrid } as PdfBlock.CardGrid
        cartoes.cards()*.eyebrow == ["Cliente", "Ensaio", "Pacote", "Valores"]

        and: "as cláusulas do template viram seções"
        documento.blocks().any {
            it instanceof PdfBlock.SectionTitle && (it as PdfBlock.SectionTitle).text == "Dados do Cliente"
        }

        and: "a assinatura e a autenticação são blocos próprios"
        def assinaturas = documento.blocks().find { it instanceof PdfBlock.SignatureBlock } as PdfBlock.SignatureBlock
        assinaturas.signatures()*.role == ["Contratante", "Contratada"]
        assinaturas.signatures()[0].name == "Mariana Alves"
        assinaturas.signatures()[0].document == "CPF 000.000.000-00"

        def autenticacao = documento.blocks().find { it instanceof PdfBlock.MetaBox } as PdfBlock.MetaBox
        autenticacao.entries()*.label.containsAll(["Proposta", "Assinado em", "Endereço IP", "Código SHA-256"])

        and: "as linhas de assinatura em texto do template não são duplicadas"
        !documento.blocks().any {
            it instanceof PdfBlock.Paragraph && (it as PdfBlock.Paragraph).text().startsWith("Assinatura do contratante")
        }

        and: "o rodapé identifica a contratada"
        documento.footerLeft().contains("Carol Oliva Fotografia")
        documento.footerLeft().contains("00.000.000/0001-00")
    }

    def "gera PDF final legível sem placeholders pendentes"() {
        given:
        def proposta = agendamento()
        def placeholders = placeholdersValidos()
        def documento = composer.compor(proposta, placeholders, textoPadrao(placeholders),
            new TermoPdfComposer.DadosAssinatura("Mariana Alves",
                LocalDateTime.of(2026, 10, 24, 15, 42), "203.0.113.7", "abc123"))

        when:
        def bytes = pdfWriter.gerar(documento)

        then:
        new String(bytes, 0, 5, "US-ASCII") == "%PDF-"

        and:
        def texto = textoDe(bytes).toLowerCase()
        texto.contains("carol oliva fotografia")
        texto.contains("mariana alves")
        texto.contains("praia do rosa")
        texto.contains("dados do cliente")
        texto.contains("autenticação do documento")
        texto.contains("página 1 de")
        !texto.contains("{{")
        !texto.contains("}}")
    }

    def "omite o destaque de PIX quando a chave não está configurada"() {
        given:
        def placeholders = placeholdersValidos() + [pixChave: "", pixTipoChave: ""]

        when:
        def documento = composer.compor(agendamento(), placeholders, textoPadrao(placeholders),
            new TermoPdfComposer.DadosAssinatura("Mariana Alves",
                LocalDateTime.of(2026, 10, 24, 15, 42), null, "abc123"))

        then:
        !documento.blocks().any { it instanceof PdfBlock.Callout }
    }

    def "usa o nome do cliente como assinante quando o aceite não informa assinante"() {
        given:
        def placeholders = placeholdersValidos()

        when:
        def documento = composer.compor(agendamento(), placeholders, textoPadrao(placeholders),
            new TermoPdfComposer.DadosAssinatura(null,
                LocalDateTime.of(2026, 10, 24, 15, 42), "127.0.0.1", "abc123"))

        then:
        def assinaturas = documento.blocks().find { it instanceof PdfBlock.SignatureBlock } as PdfBlock.SignatureBlock
        assinaturas.signatures()[0].name == "Mariana Alves"
    }

    @Unroll
    def "deriva o monograma de #nome como #esperado"() {
        expect:
        TermoPdfComposer.monograma(nome) == esperado

        where:
        nome                          | esperado
        "Carol Oliva Fotografia"      | "CO"
        "Carol de Oliva"              | "CO"
        "Luz"                         | "L"
        "Studio da Luz"               | "SL"
        ""                            | ""
        null                          | ""
    }

    def "remove as linhas de assinatura em texto do template"() {
        given:
        def texto = """# 9. Assinatura Digital
Ao assinar este documento, o Cliente declara que leu e concorda.
Assinatura do contratante: Mariana Alves
Assinatura da Contratada: Carol Oliva Fotografia
Aceite registrado em 24/10/2026 às 15:42 (IP 127.0.0.1)
Hash do documento: abc123
"""

        when:
        def limpo = TermoPdfComposer.limparTextoDoTemplate(texto)

        then:
        !limpo.contains("Assinatura do contratante")
        !limpo.contains("Assinatura da Contratada")
        !limpo.contains("Aceite registrado")
        !limpo.contains("Hash do documento")
        limpo.contains("Ao assinar este documento")
    }

    private String textoPadrao(Map<String, String> placeholders) {
        new PropostaTemplateService(null).renderizarTexto(ConfigKey.CONTRATO_TEMPLATE_PADRAO, placeholders)
    }

    private static Map<String, String> placeholdersValidos() {
        [
            clienteNome         : "Mariana Alves",
            clienteCPF          : "000.000.000-00",
            clienteTelefone     : "(11) 99999-9999",
            clienteEmail        : "mariana@teste.com",
            clienteCidade       : "São Paulo",
            clienteEstado       : "SP",
            dataEnsaio          : "24/10/2026",
            horarioEnsaio       : "15:00",
            duracaoEnsaio       : "2h",
            localEnsaio         : "Praia do Rosa",
            pacoteNome          : "Ensaio Casal",
            pacoteDescricao     : "- 20 fotos tratadas\n- Galeria digital",
            precoFotoExtra      : "R\$ 20,00",
            valorTotal          : "R\$ 1.800,00",
            valorEntrada        : "R\$ 540,00",
            statusReserva       : "recebida",
            percentualEntrada   : "30",
            valorRestante       : "R\$ 1.260,00",
            contratadaNome      : "Carol Oliva Fotografia",
            contratadaCnpj      : "00.000.000/0001-00",
            contratadaCidade    : "São Paulo - SP",
            pixChave            : "00.000.000/0001-00",
            pixTipoChave        : "CNPJ",
            autorizaUsoImagem   : "(X) AUTORIZO\n( ) NÃO AUTORIZO",
            taxaDeslocamento    : "R\$ 0,00",
            fotografoResponsavel: "Carol Oliva",
            profissionaisEnsaio : "Profissionais do ensaio:\n- Carol Oliva (Fotógrafo)"
        ]
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
