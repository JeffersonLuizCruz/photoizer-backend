package com.photoizer.crm.foto.service

import com.photoizer.crm.foto.api.ArmazenamentoResumoResponse
import com.photoizer.crm.foto.model.ArmazenamentoSnapshot
import com.photoizer.crm.foto.repository.ArmazenamentoSnapshotRepository
import com.photoizer.crm.foto.repository.FotoEnsaioRepository
import spock.lang.Specification
import spock.lang.Subject

import java.time.LocalDate
import java.time.ZoneId

class ArmazenamentoHistoricoServiceSpec extends Specification {

    def snapshotRepository = Mock(ArmazenamentoSnapshotRepository)
    def fotoRepository = Mock(FotoEnsaioRepository)
    def armazenamentoService = Mock(ArmazenamentoService)

    @Subject
    def service = new ArmazenamentoHistoricoService(
        snapshotRepository, fotoRepository, armazenamentoService, false)

    def data = LocalDate.of(2026, 10, 27)

    def "cria o snapshot do dia a partir do resumo e dos contadores"() {
        given:
        def resumo = resumo()
        armazenamentoService.resumo() >> resumo
        snapshotRepository.findById(data) >> Optional.empty()
        fotoRepository.countByArquivosRemovidosTrue() >> 3
        fotoRepository.somarBytesLiberadosEntre(
            data.atStartOfDay(), data.atStartOfDay().plusDays(1)) >> 1200L

        when:
        def snapshot = service.coletar(data)

        then:
        snapshot.data() == data
        snapshot.bytesFotos() == 1_000L
        snapshot.bytesDerivados() == 200L
        snapshot.bytesEditadas() == 300L
        snapshot.bytesDocumentos() == 50L
        snapshot.bytesPropostas() == 20L
        snapshot.bytesOutros() == 10L
        snapshot.bytesTotal() == 1_580L
        snapshot.volumeTotal() == 100_000L
        snapshot.volumeLivre() == 60_000L
        snapshot.totalFotos() == 10
        snapshot.totalGalerias() == 2
        snapshot.galeriasArquivadas() == 1
        snapshot.fotosArquivadas() == 3
        snapshot.bytesLiberadosNoDia() == 1_200L
        snapshot.coletadoEm() != null
        1 * snapshotRepository.save(_) >> { args -> args[0] }
    }

    def "recoletar o mesmo dia atualiza a linha em vez de criar outra"() {
        given:
        def existente = ArmazenamentoSnapshot.builder()
            .data(data)
            .bytesTotal(1L)
            .build()
        armazenamentoService.resumo() >> resumo()
        snapshotRepository.findById(data) >> Optional.of(existente)
        fotoRepository.countByArquivosRemovidosTrue() >> 0
        fotoRepository.somarBytesLiberadosEntre(_, _) >> 0L

        when:
        def snapshot = service.coletar(data)

        then:
        snapshot.data() == data
        snapshot.bytesTotal() == 1_580L
        existente.bytesTotal == 1_580L
        1 * snapshotRepository.save(existente) >> existente
    }

    def "listar devolve a série mapeada e limita o período"() {
        given:
        def snapshot = ArmazenamentoSnapshot.builder()
            .data(data)
            .bytesTotal(500L)
            .coletadoEm(java.time.LocalDateTime.now())
            .build()
        snapshotRepository.findByDataGreaterThanEqualOrderByDataAsc(_) >> [snapshot]

        when:
        def historico = service.listar(9999)

        then:
        historico.dias() == 730
        historico.snapshots().size() == 1
        historico.snapshots().first().data() == data
        historico.snapshots().first().bytesTotal() == 500L
    }

    def "coletar hoje usa o fuso do negócio"() {
        given:
        armazenamentoService.resumo() >> resumo()
        snapshotRepository.findById(_) >> Optional.empty()
        fotoRepository.countByArquivosRemovidosTrue() >> 0
        fotoRepository.somarBytesLiberadosEntre(_, _) >> 0L
        snapshotRepository.save(_) >> { args -> args[0] }

        when:
        def snapshot = service.coletarHoje()

        then:
        snapshot.data() == LocalDate.now(ZoneId.of('America/Sao_Paulo'))
    }

    def "dias zero vira um dia"() {
        given:
        snapshotRepository.findByDataGreaterThanEqualOrderByDataAsc(_) >> []

        expect:
        service.listar(0).dias() == 1
        service.listar(-5).dias() == 1
    }

    private ArmazenamentoResumoResponse resumo() {
        return new ArmazenamentoResumoResponse(
            1_000L,
            200L,
            300L,
            50L,
            20L,
            10L,
            1_580L,
            100_000L,
            60_000L,
            40.0,
            2,
            10,
            1,
            500L
        )
    }
}
