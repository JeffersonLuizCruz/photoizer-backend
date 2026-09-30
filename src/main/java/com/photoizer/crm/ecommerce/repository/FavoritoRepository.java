package com.photoizer.crm.ecommerce.repository;

import com.photoizer.crm.ecommerce.model.Favorito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FavoritoRepository extends JpaRepository<Favorito, UUID> {

    List<Favorito> findBySessionIdAndAgendamentoIdOrderByAuditInfoCreatedAtAsc(UUID sessionId, UUID agendamentoId);

    Optional<Favorito> findBySessionIdAndFotoId(UUID sessionId, UUID fotoId);

    void deleteBySessionIdAndFotoId(UUID sessionId, UUID fotoId);

    /** Contagem de curtidas por foto (sessões distintas) em um agendamento. */
    @Query("SELECT f.fotoId, COUNT(DISTINCT f.sessionId) FROM Favorito f " +
           "WHERE f.agendamentoId = :agendamentoId GROUP BY f.fotoId")
    List<Object[]> contarFavoritosPorFoto(@Param("agendamentoId") UUID agendamentoId);
}
