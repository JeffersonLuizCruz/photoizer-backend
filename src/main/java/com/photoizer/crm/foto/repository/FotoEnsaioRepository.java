package com.photoizer.crm.foto.repository;

import com.photoizer.crm.foto.model.FotoEnsaio;
import com.photoizer.crm.foto.model.StatusFoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FotoEnsaioRepository extends JpaRepository<FotoEnsaio, UUID> {

    List<FotoEnsaio> findByAgendamentoIdOrderByOrdemAsc(UUID agendamentoId);

    List<FotoEnsaio> findByAgendamentoIdAndStatusOrderByOrdemAsc(UUID agendamentoId, StatusFoto status);

    int countByAgendamentoId(UUID agendamentoId);

    int countByAgendamentoIdAndStatus(UUID agendamentoId, StatusFoto status);

    @Query("SELECT COUNT(f) FROM FotoEnsaio f WHERE f.agendamentoId = :agendamentoId AND f.selecionadaPacote = true")
    int countSelecionadasPacoteByAgendamentoId(@Param("agendamentoId") UUID agendamentoId);

    @Query("SELECT COUNT(f) FROM FotoEnsaio f WHERE f.agendamentoId = :agendamentoId AND f.status = 'PAGA'")
    int countPagasByAgendamentoId(@Param("agendamentoId") UUID agendamentoId);

    List<FotoEnsaio> findByAgendamentoIdAndCategoria(UUID agendamentoId, String categoria);

    List<FotoEnsaio> findByAgendamentoIdAndTagsContaining(UUID agendamentoId, String tag);
}
