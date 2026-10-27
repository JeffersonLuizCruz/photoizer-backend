package com.photoizer.crm.foto.repository

import com.photoizer.crm.foto.model.FotoEnsaio
import com.photoizer.crm.foto.model.StatusFoto
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.TestPropertySource
import spock.lang.Specification

import java.time.LocalDateTime

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:fotoarmazenamentorepo;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
])
class FotoArmazenamentoRepositorySpec extends Specification {

    @Autowired
    FotoEnsaioRepository repository

    def agendamentoA = UUID.randomUUID()
    def agendamentoB = UUID.randomUUID()

    def "agrega total e quantidade de arquivadas por agendamento"() {
        given:
        def arquivada = foto(agendamentoA)
        arquivada.setArquivosRemovidos(true)
        repository.saveAll([foto(agendamentoA), arquivada, foto(agendamentoB)])
        repository.flush()

        when:
        def porId = repository.contarPorAgendamento()
            .collectEntries { [(it.agendamentoId): it] }

        then:
        porId[agendamentoA].total == 2
        porId[agendamentoA].arquivadas == 1
        porId[agendamentoB].total == 1
        porId[agendamentoB].arquivadas == 0
    }

    def "soma bytes e data do arquivamento apenas das fotos expurgadas"() {
        given:
        def em = LocalDateTime.of(2026, 1, 2, 3, 4)
        def arquivada = foto(agendamentoA)
        arquivada.setArquivosRemovidos(true)
        arquivada.setArquivosRemovidosEm(em)
        arquivada.setBytesLiberados(500L)
        repository.saveAll([arquivada, foto(agendamentoA)])
        repository.flush()

        when:
        def arquivamento = repository.arquivamentoPorAgendamento()
            .find { it.agendamentoId == agendamentoA }

        then:
        arquivamento != null
        arquivamento.bytesLiberados == 500L
        arquivamento.arquivadaEm == em
    }

    def "conta pendências de seleção ativa e compra aguardando confirmação"() {
        given:
        def emSelecao = foto(agendamentoA)
        emSelecao.setEmSelecaoEdicao(true)
        def aguardando = foto(agendamentoA)
        aguardando.setStatus(StatusFoto.AGUARDANDO_CONFIRMACAO)
        def paga = foto(agendamentoA)
        paga.setStatus(StatusFoto.PAGA)
        repository.saveAll([emSelecao, aguardando, paga])
        repository.flush()

        when:
        def pendencia = repository.pendenciasPorAgendamento(
            [StatusFoto.AGUARDANDO_COMPROVANTE, StatusFoto.AGUARDANDO_CONFIRMACAO])
            .find { it.agendamentoId == agendamentoA }

        then:
        pendencia != null
        pendencia.pendencias == 2
    }

    def "último download usa a data mais recente por agendamento"() {
        given:
        def primeiro = foto(agendamentoA)
        primeiro.setDataDownload(LocalDateTime.of(2026, 2, 1, 10, 0))
        def ultimo = foto(agendamentoA)
        ultimo.setDataDownload(LocalDateTime.of(2026, 2, 3, 10, 0))
        repository.saveAll([primeiro, ultimo, foto(agendamentoB)])
        repository.flush()

        when:
        def downloads = repository.ultimoDownloadPorAgendamento()
            .collectEntries { [(it.agendamentoId): it.ultimoDownload] }

        then:
        downloads[agendamentoA] == LocalDateTime.of(2026, 2, 3, 10, 0)
        downloads[agendamentoB] == null
    }

    private FotoEnsaio foto(UUID agendamentoId) {
        return FotoEnsaio.builder()
            .agendamentoId(agendamentoId)
            .fileName("IMG_${UUID.randomUUID()}.jpg")
            .originalPath("orig/${UUID.randomUUID()}.jpg")
            .watermarkedPath("orig/wm_${UUID.randomUUID()}.jpg")
            .thumbPath("orig/thumb_${UUID.randomUUID()}.jpg")
            .ordem(0)
            .status(StatusFoto.PUBLICADA)
            .visivel(true)
            .build()
    }
}
