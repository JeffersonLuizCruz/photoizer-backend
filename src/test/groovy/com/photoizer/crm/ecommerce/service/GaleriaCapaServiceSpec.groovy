package com.photoizer.crm.ecommerce.service

import com.photoizer.crm.config.service.MarcaDaguaService
import com.photoizer.crm.ecommerce.model.FaseGaleria
import com.photoizer.crm.ecommerce.model.GaleriaEnsaio
import com.photoizer.crm.ecommerce.model.ModoGaleria
import com.photoizer.crm.foto.model.FotoEnsaio
import com.photoizer.crm.foto.model.StatusFoto
import com.photoizer.crm.foto.model.VersaoFoto
import com.photoizer.crm.foto.repository.FotoEnsaioRepository
import com.photoizer.crm.shared.processing.ImageProcessingService
import com.photoizer.crm.shared.storage.StoragePathResolver
import spock.lang.Specification
import spock.lang.Subject

class GaleriaCapaServiceSpec extends Specification {

    def agendamentoId = UUID.randomUUID()
    def galeriaQueryService = Mock(GaleriaQueryService)
    def galeriaEnsaioService = Mock(GaleriaEnsaioService)
    def fotoRepository = Mock(FotoEnsaioRepository)
    def marcaDaguaService = Mock(MarcaDaguaService)
    def imageProcessingService = Mock(ImageProcessingService)
    def pathResolver = Mock(StoragePathResolver)

    @Subject
    def service = new GaleriaCapaService(
        galeriaQueryService,
        galeriaEnsaioService,
        fotoRepository,
        marcaDaguaService,
        imageProcessingService,
        pathResolver
    )

    def "retorna null quando a galeria nao tem fotos publicas"() {
        given:
        def galeria = galeria(null, null)

        expect:
        service.resolver(galeria, []) == null
    }

    def "usa a capa manual quando ela existe na lista publica"() {
        given:
        def capa = foto(1)
        def outra = foto(2)
        def galeria = galeria(capa.id, VersaoFoto.ORIGINAL)

        when:
        def resolvida = service.resolver(galeria, [outra, capa])

        then:
        resolvida.foto().id == capa.id
        resolvida.versao() == VersaoFoto.ORIGINAL
    }

    def "cai na foto em destaque quando a capa manual nao esta na lista"() {
        given:
        def primeira = foto(1)
        def destaque = foto(2).tap { it.destaque = true }
        def galeria = galeria(UUID.randomUUID(), VersaoFoto.ORIGINAL)

        when:
        def resolvida = service.resolver(galeria, [primeira, destaque])

        then:
        resolvida.foto().id == destaque.id
    }

    def "cai na primeira foto quando nao ha capa nem destaque"() {
        given:
        def primeira = foto(1)
        def segunda = foto(2)
        def galeria = galeria(null, null)

        when:
        def resolvida = service.resolver(galeria, [primeira, segunda])

        then:
        resolvida.foto().id == primeira.id
    }

    def "fallback nao herda a versao de uma capa manual removida"() {
        given:
        def primeira = foto(1).tap {
            it.versaoAtual = VersaoFoto.EDITADA
            it.editadaPath = "editadas/1.jpg"
        }
        def galeria = galeria(null, VersaoFoto.ORIGINAL)

        when:
        def resolvida = service.resolver(galeria, [primeira])

        then:
        resolvida.versao() == VersaoFoto.EDITADA
    }

    def "congela a versao escolhida e nao troca sozinha depois da edicao"() {
        given:
        def capa = foto(1).tap {
            it.versaoAtual = VersaoFoto.EDITADA
            it.editadaPath = "editadas/1.jpg"
        }
        def galeria = galeria(capa.id, VersaoFoto.ORIGINAL)

        when:
        def resolvida = service.resolver(galeria, [capa])

        then:
        resolvida.versao() == VersaoFoto.ORIGINAL
    }

    def "usa a versao editada quando a capa foi escolhida depois da publicacao"() {
        given:
        def capa = foto(1).tap {
            it.versaoAtual = VersaoFoto.EDITADA
            it.editadaPath = "editadas/1.jpg"
        }
        def galeria = galeria(capa.id, VersaoFoto.EDITADA)

        when:
        def resolvida = service.resolver(galeria, [capa])

        then:
        resolvida.versao() == VersaoFoto.EDITADA
    }

    private GaleriaEnsaio galeria(UUID capaFotoId, VersaoFoto capaVersao) {
        return GaleriaEnsaio.builder()
            .agendamentoId(agendamentoId)
            .modo(ModoGaleria.COM_EDICAO)
            .fase(FaseGaleria.SELECAO)
            .capaFotoId(capaFotoId)
            .capaVersao(capaVersao)
            .build()
    }

    private FotoEnsaio foto(int numero) {
        return FotoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .fileName("IMG_${numero}.jpg")
            .originalPath("orig/${numero}.jpg")
            .watermarkedPath("wm/${numero}.jpg")
            .thumbPath("thumb/${numero}.jpg")
            .numeroGaleria(numero)
            .versaoAtual(VersaoFoto.ORIGINAL)
            .status(StatusFoto.PUBLICADA)
            .visivel(true)
            .build()
    }
}
