package com.photoizer.crm.bloqueio.repository;

import com.photoizer.crm.bloqueio.model.BloqueioAgenda;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class BloqueioAgendaRepositoryTest {

    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 11, 20, 8, 0);
    private static final LocalDateTime FIM = LocalDateTime.of(2026, 11, 20, 18, 0);
    private static final UUID PROFISSIONAL_A = UUID.randomUUID();
    private static final UUID PROFISSIONAL_B = UUID.randomUUID();

    @Autowired
    private BloqueioAgendaRepository repository;

    @Test
    @DisplayName("consulta aplicável inclui globais e o profissional informado, mas não outro profissional")
    void consultaAplicavelRespeitaEscopo() {
        repository.save(bloqueio("Global", null));
        repository.save(bloqueio("Profissional A", PROFISSIONAL_A));
        repository.save(bloqueio("Profissional B", PROFISSIONAL_B));

        var paraA = repository.findAtivosAplicaveisNoPeriodo(INICIO, FIM, PROFISSIONAL_A);
        var paraB = repository.findAtivosAplicaveisNoPeriodo(INICIO, FIM, PROFISSIONAL_B);
        var semProfissional = repository.findAtivosAplicaveisNoPeriodo(INICIO, FIM, null);

        assertThat(paraA).extracting(BloqueioAgenda::getTitulo)
            .containsExactlyInAnyOrder("Global", "Profissional A");
        assertThat(paraB).extracting(BloqueioAgenda::getTitulo)
            .containsExactlyInAnyOrder("Global", "Profissional B");
        assertThat(semProfissional).extracting(BloqueioAgenda::getTitulo)
            .containsExactly("Global");
    }

    @Test
    @DisplayName("consulta da agenda retorna todos os bloqueios ativos no período")
    void consultaDaAgendaRetornaTodos() {
        repository.save(bloqueio("Global", null));
        repository.save(bloqueio("Profissional A", PROFISSIONAL_A));
        var cancelado = bloqueio("Cancelado", null);
        cancelado.cancelar(UUID.randomUUID());
        repository.save(cancelado);

        var todos = repository.findAtivosNoPeriodo(INICIO, FIM);

        assertThat(todos).extracting(BloqueioAgenda::getTitulo)
            .containsExactlyInAnyOrder("Global", "Profissional A");
    }

    @Test
    @DisplayName("não retorna bloqueio fora do período consultado")
    void naoRetornaForaDoPeriodo() {
        repository.save(bloqueio("Amanhã", null, INICIO.plusDays(1), FIM.plusDays(1)));

        var todos = repository.findAtivosNoPeriodo(INICIO, FIM);

        assertThat(todos).isEmpty();
    }

    private BloqueioAgenda bloqueio(String titulo, UUID profissionalId) {
        return bloqueio(titulo, profissionalId, INICIO, FIM);
    }

    private BloqueioAgenda bloqueio(String titulo, UUID profissionalId,
                                    LocalDateTime inicio, LocalDateTime fim) {
        return BloqueioAgenda.builder()
            .titulo(titulo)
            .inicio(inicio)
            .fim(fim)
            .profissionalId(profissionalId)
            .criadoPorId(UUID.randomUUID())
            .ativo(true)
            .build();
    }
}
