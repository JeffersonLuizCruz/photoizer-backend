package com.photoizer.crm.shared.logging

import org.slf4j.MDC
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import spock.lang.Specification

class UserMdcFilterSpec extends Specification {

    def filter = new UserMdcFilter()

    def cleanup() {
        SecurityContextHolder.clearContext()
        MDC.clear()
    }

    def "publica userId quando o principal é o UUID em String do JWT"() {
        given:
        def userId = UUID.randomUUID()
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                userId.toString(), null, [new SimpleGrantedAuthority("ROLE_ADMIN")]))
        def request = new MockHttpServletRequest("GET", "/api/v1/agenda")

        when:
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain())

        then:
        MDC.get(LoggingKeys.USER_ID) == userId.toString()
        MDC.get(LoggingKeys.USER_ROLE) == "ROLE_ADMIN"
    }

    def "aceita principal UUID (testes/futuro)"() {
        given:
        def userId = UUID.randomUUID()
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(userId, null, []))

        when:
        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/agenda"),
            new MockHttpServletResponse(), new MockFilterChain())

        then:
        MDC.get(LoggingKeys.USER_ID) == userId.toString()
    }

    def "não publica username/e-mail de principal legado"() {
        given:
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("joao@example.com", null, []))
        def request = new MockHttpServletRequest("GET", "/api/v1/agenda")

        when:
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain())

        then:
        MDC.get(LoggingKeys.USER_ID) == null
    }
}
