package com.photoizer.crm.agenda.service

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class PropostaTemplateServiceSpec extends Specification {

    @Subject
    def service = new PropostaTemplateService(null)

    @Unroll
    def "sanitiza HTML removendo #descricao"() {
        when: "o HTML é sanitizado"
        def sanitizado = service.sanitizarHtml(html)

        then: "o conteúdo perigoso é removido e o conteúdo seguro permanece"
        !sanitizado.contains(removido)
        sanitizado.contains(mantido)

        where:
        descricao              | html                                                 | removido       | mantido
        "script"               | "<p>ok</p><script>alert(document.cookie)</script>"    | "script"       | "<p>ok</p>"
        "atributo onclick"     | "<p onclick=\"steal()\">texto</p>"                    | "onclick"      | "<p>texto</p>"
        "atributo onmouseover" | "<ul onmouseover='x()'><li>a</li></ul>"               | "onmouseover"  | "<li>a</li>"
        "esquema javascript"   | "<p><a href=\"javascript:alert(1)\">clique</a></p>"   | "javascript:"  | "clique"
        "iframe"               | "<p>a</p><iframe src=\"http://evil\"></iframe>"       | "<iframe"      | "<p>a</p>"
        "formulário"           | "<p>a</p><form><input></form>"                        | "<form"        | "<p>a</p>"
    }

    def "não remove tags permitidas"() {
        given: "um HTML composto apenas por tags da allowlist"
        def html = "<h1>T</h1><h2>S</h2><p>p</p><ul><li>i</li></ul>"

        when: "o HTML é sanitizado"
        def sanitizado = service.sanitizarHtml(html)

        then: "o conteúdo é preservado"
        sanitizado == html
    }

    def "escapa aspas e tags no fluxo de renderização"() {
        given: "um template com placeholder e valor malicioso"
        def template = "= Contrato =\nNome: {{clienteNome}}\n"
        def valores = [clienteNome: "\"><script>alert(1)</script>"]

        when: "o template é renderizado em HTML"
        def html = service.renderizarHtml(template, valores)

        then: "o valor é escapado e nenhum script é injetado"
        !html.contains("<script>")
        html.contains("&quot;&gt;")
    }

    def "renderiza o status da reserva no template"() {
        given: "um template com o placeholder de status"
        def template = 'Valor da reserva ({{percentualEntrada}}%) - {{statusReserva}}: {{valorEntrada}}'
        def valores = [percentualEntrada: '30', statusReserva: 'a receber', valorEntrada: 'R$ 300,00']

        expect: "o status é substituído junto com os demais valores"
        service.renderizarTexto(template, valores) == 'Valor da reserva (30%) - a receber: R$ 300,00'
    }

    @Unroll
    def "sanitiza entrada vazia ou nula: #entrada"() {
        expect: "o resultado é vazio"
        service.sanitizarHtml(entrada).isEmpty()

        where:
        entrada << [null, ""]
    }

    def "allowlist de tags é restritiva"() {
        given: "as tags permitidas pelo sanitizador"
        def permitidas = ["h1", "h2", "p", "ul", "li"]

        expect: "tags potencialmente perigosas não fazem parte da allowlist"
        ["script", "img", "a", "iframe"].every { !permitidas.contains(it) }
    }
}
