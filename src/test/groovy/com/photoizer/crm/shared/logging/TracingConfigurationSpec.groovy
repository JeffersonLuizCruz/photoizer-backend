package com.photoizer.crm.shared.logging

import io.micrometer.tracing.Tracer
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import spock.lang.Specification

@SpringBootTest
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:tracing-config;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
])
class TracingConfigurationSpec extends Specification {

    @Autowired
    Tracer tracer

    def cleanup() {
        MDC.clear()
    }

    def "autoconfigura o Tracer do Micrometer via Brave"() {
        expect:
        tracer != null
    }

    def "publica traceId no MDC ao abrir um span"() {
        given:
        def span = tracer.nextSpan().name("teste-mdc").start()

        when:
        String traceIdNoMdc = null
        try (def ignored = tracer.withSpan(span)) {
            traceIdNoMdc = MDC.get("traceId")
        } finally {
            span.end()
        }

        then:
        traceIdNoMdc != null
        traceIdNoMdc == span.context().traceId()
    }
}
