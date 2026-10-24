package com.photoizer.crm.shared.logging

import spock.lang.Specification
import spock.lang.Unroll

class SensitiveDataMaskSpec extends Specification {

    @Unroll
    def "mascara #descricao"() {
        expect:
        !SensitiveDataMask.mask(entrada).contains(proibido)

        where:
        descricao                     | entrada                                        | proibido
        "CPF formatado"               | "CPF 123.456.789-01 do cliente"                | "123.456.789-01"
        "CPF sem máscara"             | "cpf=12345678901"                              | "12345678901"
        "CNPJ formatado"              | "CNPJ 12.345.678/0001-99"                      | "12.345.678/0001-99"
        "e-mail"                      | "contato: joao.silva@example.com"              | "joao.silva"
        "telefone formatado"          | "tel (11) 99999-9999"                          | "99999-9999"
        "telefone cru"                | "tel 11999999999"                              | "11999999999"
        "cartão"                      | "card 4111111111111111"                        | "4111111111111111"
        "JWT"                         | "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.abc123def456" | "eyJhbGciOiJIUzI1NiJ9"
        "senha em JSON"               | '{"senha":"minhaSenha123"}'                     | "minhaSenha123"
        "token em query"              | "token=abc123def456"                           | "abc123def456"
        "authorization"               | 'authorization=Bearer abcdefgh12345678'         | "abcdefgh12345678"
        "record/DTO com PII"          | new DtoPii().toString()                         | "123.456.789-01"
    }

    def "mascara o toString de objetos em maskArgs"() {
        when:
        def resultado = SensitiveDataMask.maskArgs([new DtoPii(), null] as Object[])

        then:
        resultado.contains("123") == false
        resultado.contains("joao.silva") == false
        resultado.contains("null")
    }

    def "preserva dados que não são PII"() {
        given:
        def uuid = "9f1c2d3e-4a5b-6c7d-8e9f-0a1b2c3d4e5f"

        expect:
        SensitiveDataMask.mask(uuid) == uuid
        SensitiveDataMask.mask("valor 1234.56") == "valor 1234.56"
        SensitiveDataMask.mask("status PRE_RESERVA") == "status PRE_RESERVA"
        SensitiveDataMask.mask((String) null) == null
        SensitiveDataMask.mask("") == ""
    }

    def "trunca depois de mascarar"() {
        given:
        def entrada = "123.456.789-01 " + ("x" * 5_000)

        when:
        def resultado = SensitiveDataMask.mask(entrada)

        then:
        resultado.endsWith("...")
        resultado.length() <= 2_003
        !resultado.contains("123.456.789-01")
    }
}

/** Simula um DTO cujo toString() expõe PII (o caso que vazava antes). */
class DtoPii {
    @Override
    String toString() {
        "DtoPii[cpf=123.456.789-01, email=joao.silva@example.com, telefone=(11) 99999-9999]"
    }
}
