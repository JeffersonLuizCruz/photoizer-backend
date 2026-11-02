package com.photoizer.crm.shared.security

import com.photoizer.crm.auth.config.JwtTokenProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.test.context.TestPropertySource
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Regressão do download de originais em streaming: o Spring Security reavalia
 * a autorização no dispatch ASYNC das respostas {@link StreamingResponseBody}.
 * Quando o ASYNC não é liberado, a negação acontece depois de o corpo já ter
 * sido escrito e o Tomcat aborta a resposta sem o chunk final — o navegador
 * inicia o download e ele nunca conclui.
 *
 * <p>Usa servidor real (e não MockMvc) porque o MockMvc mantém o
 * {@code SecurityContext} no thread do teste e não reproduz a perda de contexto
 * que ocorre no dispatch ASYNC do container.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:download-streaming;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "app.testes.download-streaming=true",
])
class DownloadStreamingAutorizacaoSpec extends Specification {

    static final byte[] CONTEUDO = "conteudo-do-arquivo".getBytes(StandardCharsets.UTF_8)

    @LocalServerPort
    int porta

    @Autowired
    JwtTokenProvider jwtTokenProvider

    def "download streaming protegido conclui a resposta autenticada"() {
        when: 'o staff autenticado baixa o arquivo'
        def resposta = baixar(tokenDoStaff())

        then: 'o corpo chega completo, sem truncamento no dispatch ASYNC'
        resposta.statusCode() == 200
        resposta.body() == CONTEUDO
    }

    def "download streaming continua exigindo autenticação"() {
        when:
        def resposta = baixar(null)

        then:
        resposta.statusCode() == 401
    }

    private HttpResponse<byte[]> baixar(String token) {
        def request = HttpRequest.newBuilder(URI.create("http://localhost:${porta}/api/v1/teste/download-streaming"))
            .timeout(Duration.ofSeconds(20))
            .GET()
        if (token) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer ${token}")
        }
        return HttpClient.newHttpClient()
            .send(request.build(), HttpResponse.BodyHandlers.ofByteArray())
    }

    private String tokenDoStaff() {
        return jwtTokenProvider.generateToken(
            UUID.randomUUID(), "staff@photoizer.com", "ADMIN")
    }

    /**
     * Endpoint mínimo do fluxo {@link StreamingResponseBody} protegido. Fica atrás
     * de uma flag para existir apenas no contexto deste spec.
     */
    @RestController
    @ConditionalOnProperty(name = "app.testes.download-streaming", havingValue = "true")
    static class StreamingProtegidoController {

        @GetMapping("/api/v1/teste/download-streaming")
        ResponseEntity<StreamingResponseBody> download() {
            StreamingResponseBody body = { outputStream -> outputStream.write(CONTEUDO) } as StreamingResponseBody
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, 'attachment; filename="teste.bin"')
                .body(body)
        }
    }
}
