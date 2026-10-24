package com.photoizer.crm.shared.pdf

import spock.lang.Specification
import spock.lang.Unroll

class PdfMarkupParserSpec extends Specification {

    def "interpreta título, seção, lista e pares rótulo/valor"() {
        given: "o início do template padrão do contrato"
        def texto = """= PRESTAÇÃO DE SERVIÇOS FOTOGRÁFICOS =

# 1. Dados do Cliente
Nome completo: Mariana Alves
CPF: 000.000.000-00

# 3. Pacote Contratado
Pacote: Ensaio Casal
Inclui:
- 20 fotos tratadas
- Galeria digital
"""

        when: "o texto é convertido em blocos"
        def blocos = PdfMarkupParser.parse(texto)

        then: "o primeiro título é consumido pelo hero"
        blocos[0] instanceof PdfBlock.SectionTitle
        (blocos[0] as PdfBlock.SectionTitle).number == "1"
        (blocos[0] as PdfBlock.SectionTitle).text == "Dados do Cliente"

        and: "os dados do cliente viram pares rótulo/valor"
        def pares = blocos.findAll { it instanceof PdfBlock.KeyValues } as List<PdfBlock.KeyValues>
        pares[0].entries*.label == ["Nome completo", "CPF"]
        pares[0].entries*.value == ["Mariana Alves", "000.000.000-00"]

        and: "o rótulo sem valor vira destaque"
        blocos.any { it instanceof PdfBlock.Subheading && (it as PdfBlock.Subheading).text == "Inclui" }

        and: "os itens viram uma lista única"
        def listas = blocos.findAll { it instanceof PdfBlock.Bullets } as List<PdfBlock.Bullets>
        listas.size() == 1
        listas[0].items == ["20 fotos tratadas", "Galeria digital"]
    }

    def "mantém parágrafos longos como texto corrido"() {
        given:
        def texto = "As fotos do ensaio serão enviadas ao Cliente em até 2 dias após a realização do ensaio para que ele faça a seleção das imagens desejadas."

        when:
        def blocos = PdfMarkupParser.parse(texto)

        then:
        blocos.size() == 1
        blocos[0] instanceof PdfBlock.Paragraph
    }

    def "trata linha de template vazio sem gerar blocos"() {
        expect:
        PdfMarkupParser.parse("   \n\n  ").isEmpty()
        PdfMarkupParser.parse(null).isEmpty()
    }

    @Unroll
    def "reconhece par rótulo/valor apenas quando faz sentido: #linha"() {
        expect:
        (PdfMarkupParser.parRotuloValor(linha) != null) == esperado

        where:
        linha                                                    | esperado
        "CPF: 000.000.000-00"                                    | true
        "Valor total do serviço: R\$ 1.800,00"                   | true
        "Contratada: Carol Oliva, inscrita no CNPJ nº 00.000.000/0001-00." | true
        "15:00"                                                  | false
        "As fotos serão entregues em formato digital."           | false
        "Dados para pagamento (PIX)"                             | false
        "http://exemplo.com"                                     | false
    }

    @Unroll
    def "classifica rótulo curto em destaque: #linha"() {
        expect:
        PdfMarkupParser.ehRotuloDestacado(linha) == esperado

        where:
        linha                               | esperado
        "Dados para pagamento (PIX)"        | true
        "Inclui:"                           | true
        "(X) AUTORIZO"                      | false
        "As fotos serão entregues em alta resolução." | false
    }
}
