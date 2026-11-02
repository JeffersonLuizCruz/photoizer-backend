package com.photoizer.crm.ecommerce.service

import com.photoizer.crm.ecommerce.model.SelecaoEnsaioItem
import com.photoizer.crm.ecommerce.model.TipoItemSelecao
import com.photoizer.crm.ecommerce.repository.SelecaoEnsaioItemRepository
import com.photoizer.crm.ecommerce.repository.SelecaoEnsaioRepository
import com.photoizer.crm.foto.model.FotoEnsaio
import com.photoizer.crm.foto.repository.FotoEnsaioRepository
import com.photoizer.crm.shared.storage.FileValidator
import com.photoizer.crm.shared.storage.StoragePathResolver
import spock.lang.Specification
import spock.lang.Subject

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream

/**
 * Regressão do download de originais da seleção: o ZIP é escrito direto no
 * stream da resposta HTTP e não pode fechá-lo, senão o Tomcat encerra o
 * chunked sem o chunk final e o navegador nunca conclui o download.
 */
class SelecaoDownloadServiceSpec extends Specification {

    @Subject
    def service = new SelecaoDownloadService(
        Mock(SelecaoEnsaioRepository),
        Mock(SelecaoEnsaioItemRepository),
        Mock(FotoEnsaioRepository),
        Mock(SelecaoEnsaioService),
        Mock(GaleriaEnsaioService),
        Mock(StoragePathResolver),
        Mock(FileValidator))

    Path dir

    def setup() {
        dir = Files.createTempDirectory("selecao-download-spec")
    }

    def cleanup() {
        dir?.toFile()?.deleteDir()
    }

    def "escreve o ZIP sem fechar o stream da resposta HTTP"() {
        given:
        def foto = foto()
        def item = item(foto, 1, TipoItemSelecao.PACOTE, 1)
        def entry = "001_${foto.id}_IMG_1.jpg"
        def arquivo = arquivo("orig_1.jpg", [1, 2, 3] as byte[])
        def zip = new SelecaoDownloadService.SelecaoZip("selecao_1.zip", [
            new SelecaoDownloadService.ItemZip(entry, arquivo, item, foto, true)
        ])
        def saida = new StreamRastreavel()

        when:
        service.escreverZip(zip, saida)

        then: 'o stream do servlet continua aberto para o Tomcat finalizar a resposta'
        !saida.fechado

        and: 'o ZIP é válido e contém o arquivo e o manifest'
        def entradas = entradas(saida.toByteArray())
        entradas.keySet() == [entry, "manifest.csv"] as Set

        and: 'o manifest descreve o item como presente'
        def manifest = new String(entradas["manifest.csv"], StandardCharsets.UTF_8)
        manifest.contains("numero_galeria;foto_id;tipo;ordem;nome_original;nome_esperado_editado;presente")
        manifest.contains(";PACOTE;1;IMG_1.jpg;$entry;SIM")
    }

    def "não escreve arquivo ausente mas registra presente=NAO no manifest"() {
        given:
        def foto = foto()
        def item = item(foto, 2, TipoItemSelecao.EXTRA, 1)
        def entry = "002_${foto.id}_IMG_2.jpg"
        def zip = new SelecaoDownloadService.SelecaoZip("selecao_2.zip", [
            new SelecaoDownloadService.ItemZip(entry, null, item, foto, false)
        ])
        def saida = new StreamRastreavel()

        when:
        service.escreverZip(zip, saida)

        then:
        !saida.fechado
        entradas(saida.toByteArray()).keySet() == ["manifest.csv"] as Set

        and:
        def manifest = new String(entradas(saida.toByteArray())["manifest.csv"], StandardCharsets.UTF_8)
        manifest.contains(";EXTRA;1;IMG_2.jpg;$entry;NAO")
        !manifest.contains(";SIM")
    }

    private FotoEnsaio foto() {
        return FotoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(UUID.randomUUID())
            .fileName("IMG_1.jpg")
            .originalPath("orig_1.jpg")
            .watermarkedPath("wm_orig_1.jpg")
            .thumbPath("thumb_orig_1.jpg")
            .build()
    }

    private SelecaoEnsaioItem item(FotoEnsaio foto, int numero, TipoItemSelecao tipo, int ordem) {
        return SelecaoEnsaioItem.builder()
            .id(UUID.randomUUID())
            .selecaoId(UUID.randomUUID())
            .fotoId(foto.id)
            .numeroGaleria(numero)
            .tipo(tipo)
            .ordem(ordem)
            .nomeArquivo("IMG_${numero}.jpg")
            .build()
    }

    private Path arquivo(String nome, byte[] conteudo) {
        def caminho = dir.resolve(nome)
        Files.write(caminho, conteudo)
        return caminho
    }

    private Map<String, byte[]> entradas(byte[] bytes) {
        def resultado = [:]
        new ZipInputStream(new ByteArrayInputStream(bytes)).withCloseable { zis ->
            def entry
            while ((entry = zis.nextEntry) != null) {
                resultado[entry.name] = zis.readAllBytes()
            }
        }
        return resultado
    }

    /** ByteArrayOutputStream que registra se o consumidor fechou o stream. */
    private static class StreamRastreavel extends ByteArrayOutputStream {
        boolean fechado = false

        @Override
        void close() throws IOException {
            fechado = true
            super.close()
        }
    }
}
