package com.photoizer.crm.shared.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import spock.lang.Specification

class CorrelationIdFilterSpec extends Specification {

    def filter = new CorrelationIdFilter()

    def cleanup() {
        MDC.clear()
    }

    def "gera UUID quando o header não vem na requisição"() {
        given:
        def request = new MockHttpServletRequest("GET", "/api/v1/agenda")
        def response = new MockHttpServletResponse()
        String durante = null
        FilterChain chain = { req, res -> durante = MDC.get(LoggingKeys.CORRELATION_ID) } as FilterChain

        when:
        filter.doFilter(request, response, chain)

        then:
        response.getHeader(CorrelationIdFilter.HEADER) ==~ /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/
        durante == response.getHeader(CorrelationIdFilter.HEADER)
        MDC.get(LoggingKeys.CORRELATION_ID) == null
    }

    def "reutiliza o X-Correlation-ID válido enviado pelo cliente"() {
        given:
        def request = new MockHttpServletRequest("GET", "/api/v1/agenda")
        request.addHeader(CorrelationIdFilter.HEADER, "chamado-123")
        def response = new MockHttpServletResponse()
        String durante = null
        FilterChain chain = { req, res -> durante = MDC.get(LoggingKeys.CORRELATION_ID) } as FilterChain

        when:
        filter.doFilter(request, response, chain)

        then:
        durante == "chamado-123"
        response.getHeader(CorrelationIdFilter.HEADER) == "chamado-123"
        MDC.get(LoggingKeys.CORRELATION_ID) == null
    }

    def "descarta header inválido e gera um novo ID"() {
        given:
        def request = new MockHttpServletRequest("GET", "/api/v1/agenda")
        request.addHeader(CorrelationIdFilter.HEADER, "abc\nX-Injected: true")
        def response = new MockHttpServletResponse()
        FilterChain chain = { req, res -> } as FilterChain

        when:
        filter.doFilter(request, response, chain)

        then:
        response.getHeader(CorrelationIdFilter.HEADER) != "abc\nX-Injected: true"
        response.getHeader(CorrelationIdFilter.HEADER) ==~ /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/
    }

    def "limpa o MDC mesmo quando a cadeia lança exceção"() {
        given:
        def request = new MockHttpServletRequest("GET", "/api/v1/agenda")
        def response = new MockHttpServletResponse()
        FilterChain chain = { req, res -> throw new ServletException("falha") } as FilterChain

        when:
        filter.doFilter(request, response, chain)

        then:
        thrown(ServletException)
        MDC.get(LoggingKeys.CORRELATION_ID) == null
    }
}
