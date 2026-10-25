package com.photoizer.crm.foto.api

import com.photoizer.crm.ecommerce.model.Favorito
import com.photoizer.crm.ecommerce.model.FotoComentario
import com.photoizer.crm.ecommerce.model.ItemCarrinho
import com.photoizer.crm.ecommerce.model.OrigemComentario
import com.photoizer.crm.ecommerce.repository.FavoritoRepository
import com.photoizer.crm.ecommerce.repository.FotoComentarioRepository
import com.photoizer.crm.ecommerce.repository.ItemCarrinhoRepository
import com.photoizer.crm.foto.model.FotoEnsaio
import com.photoizer.crm.foto.model.StatusFoto
import com.photoizer.crm.foto.repository.FotoEnsaioRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.transaction.annotation.Transactional
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["spring.datasource.url=jdbc:h2:mem:foto-controller;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"])
class FotoControllerIntegrationSpec extends Specification {

    @Autowired
    MockMvc mockMvc

    @Autowired
    FotoEnsaioRepository fotoRepository

    @Autowired
    FavoritoRepository favoritoRepository

    @Autowired
    ItemCarrinhoRepository itemCarrinhoRepository

    @Autowired
    FotoComentarioRepository fotoComentarioRepository

    def agendamentoId = UUID.randomUUID()
    def arquivosDir = Files.createTempDirectory("foto-controller-spec")

    def cleanup() {
        arquivosDir.toFile().deleteDir()
    }

    def "remove fotos em lote via endpoint"() {
        given:
        def primeira = novaFoto()
        def segunda = novaFoto()

        when:
        def resultado = mockMvc.perform(post("/api/v1/agendamentos/${agendamentoId}/fotos/remover-lote")
            .contentType(MediaType.APPLICATION_JSON)
            .with(user("staff").roles("ADMIN"))
            .content("""{"fotoIds":["${primeira.id}","${segunda.id}"]}"""))

        then:
        resultado.andExpect(status().isOk())
            .andExpect(jsonPath('$.removidas').value(2))
        fotoRepository.findById(primeira.id).isEmpty()
        fotoRepository.findById(segunda.id).isEmpty()
        !Files.exists(Path.of(primeira.originalPath))
        !Files.exists(Path.of(segunda.originalPath))
    }

    def "limpa referencias do ecommerce ao remover foto em lote"() {
        given:
        def foto = novaFoto()
        def sessionId = UUID.randomUUID()
        favoritoRepository.save(Favorito.builder()
            .agendamentoId(agendamentoId)
            .fotoId(foto.id)
            .sessionId(sessionId)
            .build())
        itemCarrinhoRepository.save(ItemCarrinho.builder()
            .agendamentoId(agendamentoId)
            .fotoId(foto.id)
            .sessionId(sessionId)
            .build())
        fotoComentarioRepository.save(FotoComentario.builder()
            .agendamentoId(agendamentoId)
            .fotoId(foto.id)
            .mensagem("Comentário")
            .origem(OrigemComentario.CLIENTE)
            .lida(false)
            .build())

        when:
        def resultado = mockMvc.perform(post("/api/v1/agendamentos/${agendamentoId}/fotos/remover-lote")
            .contentType(MediaType.APPLICATION_JSON)
            .with(user("staff").roles("ADMIN"))
            .content("""{"fotoIds":["${foto.id}"]}"""))

        then:
        resultado.andExpect(status().isOk())
        favoritoRepository.findBySessionIdAndFotoId(sessionId, foto.id).isEmpty()
        itemCarrinhoRepository.findBySessionIdAndAgendamentoIdOrderByAuditInfoCreatedAtAsc(sessionId, agendamentoId).isEmpty()
        fotoComentarioRepository.findByFotoIdOrderByAuditInfoCreatedAtAsc(foto.id).isEmpty()
    }

    def "bloqueia remocao em lote de foto vendida"() {
        given:
        def foto = novaFoto(StatusFoto.PAGA)

        when:
        def resultado = mockMvc.perform(post("/api/v1/agendamentos/${agendamentoId}/fotos/remover-lote")
            .contentType(MediaType.APPLICATION_JSON)
            .with(user("staff").roles("ADMIN"))
            .content("""{"fotoIds":["${foto.id}"]}"""))

        then:
        resultado.andExpect(status().isConflict())
            .andExpect(jsonPath('$.code').value("FOTO_VINCULADA_A_COMPRA"))
        fotoRepository.findById(foto.id).isPresent()
        Files.exists(Path.of(foto.originalPath))
    }

    def "rejeita lote vazio"() {
        when:
        def resultado = mockMvc.perform(post("/api/v1/agendamentos/${agendamentoId}/fotos/remover-lote")
            .contentType(MediaType.APPLICATION_JSON)
            .with(user("staff").roles("ADMIN"))
            .content('{"fotoIds":[]}'))

        then:
        resultado.andExpect(status().isUnprocessableEntity())
    }

    private FotoEnsaio novaFoto(StatusFoto status = StatusFoto.PUBLICADA) {
        def paths = (1..3).collect { indice ->
            def caminho = arquivosDir.resolve("foto-${UUID.randomUUID()}-${indice}.jpg")
            Files.write(caminho, "x".bytes)
            caminho
        }
        return fotoRepository.save(FotoEnsaio.builder()
            .agendamentoId(agendamentoId)
            .originalPath(paths[0].toString())
            .watermarkedPath(paths[1].toString())
            .thumbPath(paths[2].toString())
            .fileName("foto.jpg")
            .ordem(1)
            .status(status)
            .selecionadaPacote(false)
            .visivel(true)
            .tags(["teste"])
            .build())
    }
}
