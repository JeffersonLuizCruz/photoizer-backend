package com.photoizer.crm.foto.repository

import com.photoizer.crm.foto.model.ArmazenamentoSnapshot
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.TestPropertySource
import spock.lang.Specification

import java.time.LocalDate
import java.time.LocalDateTime

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:armazenamentosnapshotrepo;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
])
class ArmazenamentoSnapshotRepositorySpec extends Specification {

    @Autowired
    ArmazenamentoSnapshotRepository repository

    def "a data é a chave: gravar o mesmo dia atualiza a linha"() {
        given:
        repository.save(snapshot(LocalDate.of(2026, 10, 26), 100L))
        repository.flush()

        when:
        repository.save(snapshot(LocalDate.of(2026, 10, 26), 250L))
        repository.flush()

        then:
        repository.count() == 1
        repository.findById(LocalDate.of(2026, 10, 26)).get().bytesTotal == 250L
    }

    def "lista em ordem ascendente a partir da data inicial"() {
        given:
        repository.saveAll([
            snapshot(LocalDate.of(2026, 10, 24), 10L),
            snapshot(LocalDate.of(2026, 10, 26), 30L),
            snapshot(LocalDate.of(2026, 10, 25), 20L),
        ])
        repository.flush()

        when:
        def serie = repository.findByDataGreaterThanEqualOrderByDataAsc(LocalDate.of(2026, 10, 25))

        then:
        serie*.data == [LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 26)]
        serie*.bytesTotal == [20L, 30L]
    }

    private ArmazenamentoSnapshot snapshot(LocalDate data, long bytesTotal) {
        return ArmazenamentoSnapshot.builder()
            .data(data)
            .bytesFotos(bytesTotal)
            .bytesTotal(bytesTotal)
            .coletadoEm(LocalDateTime.now())
            .build()
    }
}
