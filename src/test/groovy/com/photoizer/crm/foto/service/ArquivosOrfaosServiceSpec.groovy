package com.photoizer.crm.foto.service

import com.photoizer.crm.shared.storage.ArquivosReferenciadosProvider
import com.photoizer.crm.shared.storage.StoragePathResolver
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.time.temporal.ChronoUnit

class ArquivosOrfaosServiceSpec extends Specification {

    def uploadDir = Files.createTempDirectory("orfaos-spec")
    def pathResolver = Mock(StoragePathResolver)
    def armazenamentoService = Mock(ArmazenamentoService)
    def agendamentoId = UUID.randomUUID()

    def provider = Stub(ArquivosReferenciadosProvider) {
        caminhosReferenciados() >> [
            "${agendamentoId}/orig/orig_referenciada.jpg".toString(),
            "${agendamentoId}/comprovante_entrada_referenciado.pdf".toString(),
        ]
    }

    @Subject
    def service = new ArquivosOrfaosService(pathResolver, [provider], armazenamentoService)

    def setup() {
        pathResolver.getUploadDir() >> uploadDir
    }

    def cleanup() {
        apagar(uploadDir)
    }

    def "distingue referenciado, orfao reconhecido, desconhecido e arquivo recente"() {
        given:
        def referenciado = antigo("${agendamentoId}/orig/orig_referenciada.jpg", 10)
        antigo("${agendamentoId}/orig/orig_orfa.jpg", 20)
        antigo("${agendamentoId}/comprovante_extra_orfa.pdf", 30)
        antigo("${agendamentoId}/arquivo-estranho.bin", 40)
        recente("${agendamentoId}/orig/orig_recente.jpg", 50)

        when:
        def relatorio = service.listar()

        then:
        relatorio.total == 4
        relatorio.bytes == 140
        relatorio.elegiveis == 2
        relatorio.bytesElegiveis == 50
        relatorio.desconhecidos == 1
        relatorio.arquivos.find { it.caminho.endsWith('orig_referenciada.jpg') } == null
        relatorio.arquivos.find { it.caminho.endsWith('arquivo-estranho.bin') }.elegivel == false
        relatorio.arquivos.find { it.caminho.endsWith('orig_recente.jpg') }.elegivel == false
        relatorio.arquivos.find { it.caminho.endsWith('orig_orfa.jpg') }.elegivel
        Files.exists(referenciado)
    }

    def "limpar apaga somente os orfaos elegiveis e invalida o cache"() {
        given:
        def referenciado = antigo("${agendamentoId}/orig/orig_referenciada.jpg", 10)
        def orfao = antigo("${agendamentoId}/orig/orig_orfa.jpg", 20)
        def desconhecido = antigo("${agendamentoId}/arquivo-estranho.bin", 40)
        def recente = recente("${agendamentoId}/orig/orig_recente.jpg", 50)

        when:
        def resultado = service.limpar()

        then:
        resultado.arquivos() == 1
        resultado.bytesLiberados() == 20
        !Files.exists(orfao)
        Files.exists(referenciado)
        Files.exists(desconhecido)
        Files.exists(recente)
        1 * armazenamentoService.invalidarCache()
    }

    def "proposta em PDF sem referencia e elegivel"() {
        given:
        antigo("propostas/${agendamentoId}.pdf", 25)

        when:
        def relatorio = service.listar()

        then:
        relatorio.total == 1
        relatorio.elegiveis == 1
        relatorio.arquivos.first().reconhecido
    }

    def "diretorio inexistente retorna relatorio vazio"() {
        given:
        pathResolver.getUploadDir() >> uploadDir.resolve("inexistente")

        expect:
        service.listar().total == 0
        service.limpar().arquivos() == 0
    }

    def "nunca apaga nome desconhecido mesmo antigo"() {
        given:
        def desconhecido = antigo("${agendamentoId}/backup-manual.zip", 60)

        when:
        def resultado = service.limpar()

        then:
        resultado.arquivos() == 0
        Files.exists(desconhecido)
    }

    private Path antigo(String relativo, int bytes) {
        var arquivo = escrever(relativo, bytes)
        Files.setLastModifiedTime(arquivo, FileTime.from(Instant.now().minus(48, ChronoUnit.HOURS)))
        return arquivo
    }

    private Path recente(String relativo, int bytes) {
        return escrever(relativo, bytes)
    }

    private Path escrever(String relativo, int bytes) {
        var arquivo = uploadDir.resolve(relativo)
        Files.createDirectories(arquivo.parent)
        Files.write(arquivo, new byte[bytes])
        return arquivo
    }

    private static void apagar(Path dir) {
        if (!Files.exists(dir)) {
            return
        }
        Files.walk(dir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
    }
}
