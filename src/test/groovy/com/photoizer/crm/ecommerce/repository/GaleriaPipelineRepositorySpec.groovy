package com.photoizer.crm.ecommerce.repository

import com.photoizer.crm.ecommerce.model.CompraExtra
import com.photoizer.crm.ecommerce.model.StatusCompraExtra
import com.photoizer.crm.foto.model.FotoEnsaio
import com.photoizer.crm.foto.model.StatusFoto
import com.photoizer.crm.foto.repository.FotoEnsaioRepository
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.TestPropertySource
import spock.lang.Specification

import java.time.LocalDateTime
import java.util.UUID

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:galeriapipeline-repo;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
])
class GaleriaPipelineRepositorySpec extends Specification {

    @org.springframework.beans.factory.annotation.Autowired
    FotoEnsaioRepository fotoEnsaioRepository

    @org.springframework.beans.factory.annotation.Autowired
    CompraExtraRepository compraExtraRepository

    def agendamentoId = UUID.randomUUID()

    def "agrega fotos e compras por galeria sem carregar as entidades"() {
        given: "fotos em estados diferentes"
        def compraPagaId = UUID.randomUUID()
        fotoEnsaioRepository.saveAll([
            foto(StatusFoto.PUBLICADA, true, null, false, LocalDateTime.now()),
            foto(StatusFoto.PAGA, false, compraPagaId, false, null),
            foto(StatusFoto.AGUARDANDO_CONFIRMACAO, false, UUID.randomUUID(), false, null),
            foto(StatusFoto.INEDITA, false, null, false, null),
            foto(StatusFoto.PUBLICADA, false, null, true, null)
        ])
        fotoEnsaioRepository.flush()

        and: "compras pagas, pendentes e canceladas"
        compraExtraRepository.saveAll([
            compra(new BigDecimal("150.00"), StatusCompraExtra.PAGA),
            compra(new BigDecimal("50.00"), StatusCompraExtra.AGUARDANDO_CONFIRMACAO),
            compra(new BigDecimal("30.00"), StatusCompraExtra.CANCELADA)
        ])
        compraExtraRepository.flush()

        when: "o agregado da fila é consultado"
        def fotos = fotoEnsaioRepository.agregarPipeline([agendamentoId])
        def compras = compraExtraRepository.agregarPipeline(
            [agendamentoId],
            [StatusCompraExtra.AGUARDANDO_COMPROVANTE, StatusCompraExtra.AGUARDANDO_CONFIRMACAO])

        then: "os contadores refletem o estado real"
        fotos.size() == 1
        fotos[0].total == 5
        fotos[0].publicadas == 4
        fotos[0].baixadas == 1
        fotos[0].selecionadas == 1
        fotos[0].pagas == 1
        fotos[0].extrasPendentesEdicao == 1
        fotos[0].arquivadas == 1

        and: "somente compras pagas entram no valor e no contador de pagas"
        compras.size() == 1
        compras[0].pendentes == 1
        compras[0].pagas == 1
        compras[0].valorPago == new BigDecimal("150.00")
    }

    private FotoEnsaio foto(
            StatusFoto status,
            boolean selecionada,
            UUID compraExtraId,
            boolean arquivada,
            LocalDateTime dataDownload) {
        FotoEnsaio.builder()
            .agendamentoId(agendamentoId)
            .originalPath("uploads/${UUID.randomUUID()}-orig.jpg")
            .watermarkedPath("uploads/${UUID.randomUUID()}-wm.jpg")
            .thumbPath("uploads/${UUID.randomUUID()}-thumb.jpg")
            .fileName("foto-${UUID.randomUUID()}.jpg")
            .ordem(UUID.randomUUID().hashCode())
            .status(status)
            .selecionadaPacote(selecionada)
            .compraExtraId(compraExtraId)
            .visivel(true)
            .arquivosRemovidos(arquivada)
            .dataDownload(dataDownload)
            .build()
    }

    private CompraExtra compra(BigDecimal valor, StatusCompraExtra status) {
        CompraExtra.builder()
            .agendamentoId(agendamentoId)
            .valorTotal(valor)
            .status(status)
            .quantidadeFotos(1)
            .build()
    }
}
