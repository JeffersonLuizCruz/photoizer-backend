package com.photoizer.crm.foto.service

import com.photoizer.crm.foto.acl.AgendamentoArmazenamentoReadService
import com.photoizer.crm.foto.acl.AgendamentoResumo
import com.photoizer.crm.foto.model.MotivoExpurgoBloqueado
import com.photoizer.crm.foto.repository.FotoEnsaioRepository
import com.photoizer.crm.shared.exception.NotFoundException
import com.photoizer.crm.shared.storage.StoragePathResolver
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

class ArmazenamentoServiceSpec extends Specification {

    def uploadDir = Files.createTempDirectory("armazenamento-spec")
    def pathResolver = Mock(StoragePathResolver)
    def fotoRepository = Mock(FotoEnsaioRepository)
    def agendamentoReadService = Mock(AgendamentoArmazenamentoReadService)

    @Subject
    def service = new ArmazenamentoService(pathResolver, fotoRepository, agendamentoReadService, 0)

    def agendamentoId = UUID.randomUUID()
    def dataEnsaio = LocalDateTime.of(2026, 3, 10, 14, 0)

    def setup() {
        pathResolver.getUploadDir() >> uploadDir
    }

    def cleanup() {
        apagar(uploadDir)
    }

    def "separa o consumo por tipo de arquivo"() {
        given:
        escrever("${agendamentoId}/orig", "orig_a.jpg", 100)
        escrever("${agendamentoId}/orig", "wm_orig_a.jpg", 40)
        escrever("${agendamentoId}/orig", "thumb_orig_a.jpg", 10)
        escrever("${agendamentoId}/orig", "preview_v3_orig_a.jpg", 5)
        escrever("${agendamentoId}/orig", "hero_${agendamentoId}_ORIGINAL.jpg", 3)
        escrever("${agendamentoId}/editadas", "editadas_b.jpg", 70)
        escrever("${agendamentoId}", "comprovante_entrada_c.pdf", 20)
        escrever("propostas", "${agendamentoId}.pdf", 15)
        agendamentoReadService.listar() >> [resumo()]
        fotoRepository.contarPorAgendamento() >> [contagem(agendamentoId, 2, 0)]
        fotoRepository.ultimoDownloadPorAgendamento() >> []
        fotoRepository.arquivamentoPorAgendamento() >> []
        fotoRepository.pendenciasPorAgendamento(_) >> []

        when:
        def resumo = service.resumo()

        then:
        resumo.bytesFotos() == 100
        resumo.bytesDerivados() == 58
        resumo.bytesEditadas() == 70
        resumo.bytesDocumentos() == 20
        resumo.bytesPropostas() == 15
        resumo.bytesOutros() == 0
        resumo.bytesTotal() == 263
        resumo.totalGalerias() == 1
        resumo.totalFotos() == 2
    }

    def "classifica arquivos desconhecidos como outros e não como foto"() {
        given:
        escrever(agendamentoId.toString(), "arquivo-solto.bin", 33)
        agendamentoReadService.listar() >> [resumo()]
        semAgregacoes()

        when:
        def resumo = service.resumo()

        then:
        resumo.bytesOutros() == 33
        resumo.bytesFotos() == 0
    }

    def "não quebra quando o diretório de uploads não existe"() {
        given:
        pathResolver.getUploadDir() >> uploadDir.resolve("inexistente")
        agendamentoReadService.listar() >> []
        semAgregacoes()

        when:
        def resumo = service.resumo()

        then:
        resumo.bytesTotal() == 0
        service.galerias() == []
    }

    def "lista galerias ordenadas por consumo e calcula elegibilidade"() {
        given:
        def grande = UUID.randomUUID()
        def pequena = UUID.randomUUID()
        escrever("${grande}/orig", "orig_a.jpg", 500)
        escrever("${pequena}/orig", "orig_b.jpg", 120)

        agendamentoReadService.listar() >> [
            new AgendamentoResumo(grande, "Ana", dataEnsaio, "FINALIZADO", true),
            new AgendamentoResumo(pequena, "Bruno", dataEnsaio, "EM_EDICAO", false),
        ]
        fotoRepository.contarPorAgendamento() >> [
            contagem(grande, 4, 0),
            contagem(pequena, 2, 0),
        ]
        fotoRepository.ultimoDownloadPorAgendamento() >> []
        fotoRepository.arquivamentoPorAgendamento() >> []
        fotoRepository.pendenciasPorAgendamento(_) >> []

        when:
        def galerias = service.galerias()

        then:
        galerias*.agendamentoId == [grande, pequena]
        galerias[0].bytes() == 500
        galerias[0].bytesFotos() == 500
        galerias[0].expurgavel()
        galerias[0].motivoBloqueio() == null
        galerias[1].bytes() == 120
        !galerias[1].expurgavel()
        galerias[1].motivoBloqueio() == MotivoExpurgoBloqueado.NAO_FINALIZADO
    }

    def "galeria arquivada não é elegível de novo"() {
        given:
        escrever("${agendamentoId}/orig", "orig_a.jpg", 10)
        agendamentoReadService.listar() >> [resumo()]
        agendamentoReadService.buscar(agendamentoId) >> Optional.of(resumo())
        fotoRepository.contarPorAgendamento() >> [contagem(agendamentoId, 2, 2)]
        fotoRepository.ultimoDownloadPorAgendamento() >> []
        fotoRepository.arquivamentoPorAgendamento() >> [
            arquivamento(agendamentoId, LocalDateTime.now(), 900L),
        ]
        fotoRepository.pendenciasPorAgendamento(_) >> []

        when:
        def galeria = service.galeria(agendamentoId)

        then:
        galeria.arquivada()
        !galeria.expurgavel()
        galeria.motivoBloqueio() == MotivoExpurgoBloqueado.JA_ARQUIVADA
        galeria.bytesLiberados() == 900L
        galeria.arquivadaEm() != null
    }

    def "galeria finalizada com seleção ativa fica bloqueada por pendência"() {
        given:
        agendamentoReadService.buscar(agendamentoId) >> Optional.of(resumo())
        fotoRepository.contarPorAgendamento() >> [contagem(agendamentoId, 3, 0)]
        fotoRepository.ultimoDownloadPorAgendamento() >> []
        fotoRepository.arquivamentoPorAgendamento() >> []
        fotoRepository.pendenciasPorAgendamento(_) >> [pendencia(agendamentoId, 1L)]

        when:
        def galeria = service.galeria(agendamentoId)

        then:
        !galeria.expurgavel()
        galeria.motivoBloqueio() == MotivoExpurgoBloqueado.PENDENCIA
    }

    def "consulta de galeria inexistente responde 404"() {
        given:
        agendamentoReadService.buscar(agendamentoId) >> Optional.empty()

        when:
        service.galeria(agendamentoId)

        then:
        thrown(NotFoundException)
    }

    private AgendamentoResumo resumo() {
        new AgendamentoResumo(agendamentoId, "Ana", dataEnsaio, "FINALIZADO", true)
    }

    private void semAgregacoes() {
        fotoRepository.contarPorAgendamento() >> []
        fotoRepository.ultimoDownloadPorAgendamento() >> []
        fotoRepository.arquivamentoPorAgendamento() >> []
        fotoRepository.pendenciasPorAgendamento(_) >> []
    }

    private FotoEnsaioRepository.ContagemFotos contagem(UUID id, long total, long arquivadas) {
        Stub(FotoEnsaioRepository.ContagemFotos) {
            getAgendamentoId() >> id
            getTotal() >> total
            getArquivadas() >> arquivadas
        }
    }

    private FotoEnsaioRepository.Arquivamento arquivamento(UUID id, LocalDateTime em, long bytes) {
        Stub(FotoEnsaioRepository.Arquivamento) {
            getAgendamentoId() >> id
            getArquivadaEm() >> em
            getBytesLiberados() >> bytes
        }
    }

    private FotoEnsaioRepository.PendenciaFotos pendencia(UUID id, long total) {
        Stub(FotoEnsaioRepository.PendenciaFotos) {
            getAgendamentoId() >> id
            getPendencias() >> total
        }
    }

    private Path escrever(String subdiretorio, String nome, int bytes) {
        var arquivo = uploadDir.resolve(subdiretorio).resolve(nome)
        Files.createDirectories(arquivo.parent)
        Files.write(arquivo, new byte[bytes])
        return arquivo
    }

    private static void apagar(Path dir) {
        if (!Files.exists(dir)) {
            return
        }
        Files.walk(dir)
            .sorted(Comparator.reverseOrder())
            .forEach { Files.deleteIfExists(it) }
    }
}
