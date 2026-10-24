package com.photoizer.crm.shared.logging

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import jakarta.servlet.FilterChain
import org.slf4j.LoggerFactory
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import spock.lang.Specification

class RequestLoggingFilterSpec extends Specification {

    def filter = new RequestLoggingFilter()
    def appender = new ListAppender<ILoggingEvent>()

    def setup() {
        appender.start()
        ((Logger) LoggerFactory.getLogger(RequestLoggingFilter)).addAppender(appender)
    }

    def cleanup() {
        ((Logger) LoggerFactory.getLogger(RequestLoggingFilter)).detachAppender(appender)
        appender.stop()
    }

    def "registra método, path, status e latência em uma linha"() {
        given:
        def request = new MockHttpServletRequest("POST", "/api/v1/agenda")
        def response = new MockHttpServletResponse()
        FilterChain chain = { req, res -> ((MockHttpServletResponse) res).setStatus(201) } as FilterChain

        when:
        filter.doFilter(request, response, chain)

        then:
        def evento = appender.list.last()
        evento.level.toString() == "INFO"
        evento.formattedMessage.contains("POST /api/v1/agenda -> 201")
        evento.formattedMessage.contains("ms)")
    }

    def "nunca loga a query string"() {
        given:
        def request = new MockHttpServletRequest("GET", "/api/v1/ecommerce/galeria/token")
        request.setQueryString("token=segredo-que-nao-pode-vazar")
        def response = new MockHttpServletResponse()
        FilterChain chain = { req, res -> } as FilterChain

        when:
        filter.doFilter(request, response, chain)

        then:
        !appender.list.last().formattedMessage.contains("segredo-que-nao-pode-vazar")
    }

    def "ignora OPTIONS e health check"() {
        given:
        def request = new MockHttpServletRequest("GET", "/actuator/health")
        def response = new MockHttpServletResponse()
        FilterChain chain = { req, res -> } as FilterChain

        when:
        filter.doFilter(request, response, chain)

        then:
        appender.list.isEmpty()
    }
}
