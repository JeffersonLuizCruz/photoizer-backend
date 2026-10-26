package com.photoizer.crm.foto.repository;

import com.photoizer.crm.foto.model.FotoEnsaio;
import com.photoizer.crm.foto.model.StatusFoto;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FotoEnsaioRepository extends JpaRepository<FotoEnsaio, UUID> {

    List<FotoEnsaio> findByAgendamentoIdOrderByOrdemAsc(UUID agendamentoId);

    /**
     * Carrega e bloqueia todas as fotos do agendamento para atribuicao de
     * numeracao. A lock pessimista evita que publicacoes concorrentes gerem
     * o mesmo numero; o indice unico e a segunda linha de defesa.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT f FROM FotoEnsaio f
        WHERE f.agendamentoId = :agendamentoId
        ORDER BY f.ordem ASC, f.id ASC
        """)
    List<FotoEnsaio> findAllByAgendamentoIdForUpdate(@Param("agendamentoId") UUID agendamentoId);

    List<FotoEnsaio> findByAgendamentoIdAndStatusOrderByOrdemAsc(UUID agendamentoId, StatusFoto status);

    int countByAgendamentoId(UUID agendamentoId);

    int countByAgendamentoIdAndStatus(UUID agendamentoId, StatusFoto status);

    @Query("SELECT COUNT(f) FROM FotoEnsaio f WHERE f.agendamentoId = :agendamentoId AND f.selecionadaPacote = true")
    int countSelecionadasPacoteByAgendamentoId(@Param("agendamentoId") UUID agendamentoId);

    @Query("SELECT COUNT(f) FROM FotoEnsaio f WHERE f.agendamentoId = :agendamentoId AND f.status = 'PAGA'")
    int countPagasByAgendamentoId(@Param("agendamentoId") UUID agendamentoId);

    List<FotoEnsaio> findByAgendamentoIdAndCategoria(UUID agendamentoId, String categoria);

    List<FotoEnsaio> findByAgendamentoIdAndTagsContaining(UUID agendamentoId, String tag);

    Optional<FotoEnsaio> findByFotoEdicaoId(UUID fotoEdicaoId);

    @Query("""
        SELECT f FROM FotoEnsaio f
        WHERE f.agendamentoId = :agendamentoId
          AND f.status = 'PUBLICADA'
          AND f.visivel = true
        ORDER BY COALESCE(f.numeroGaleria, 2147483647) ASC, f.ordem ASC
        """)
    List<FotoEnsaio> findPublicadasVisiveisByAgendamentoId(@Param("agendamentoId") UUID agendamentoId);

    List<FotoEnsaio> findByCompraExtraId(UUID compraExtraId);

    @Query("SELECT COALESCE(MAX(f.ordem), -1) FROM FotoEnsaio f WHERE f.agendamentoId = :agendamentoId")
    int findMaxOrdemByAgendamentoId(@Param("agendamentoId") UUID agendamentoId);
}
