package com.photoizer.crm.ecommerce.repository;

import com.photoizer.crm.ecommerce.model.Favorito;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FavoritoRepository extends JpaRepository<Favorito, UUID> {

    List<Favorito> findBySessionIdAndAgendamentoIdOrderByCreatedAtAsc(UUID sessionId, UUID agendamentoId);

    Optional<Favorito> findBySessionIdAndFotoId(UUID sessionId, UUID fotoId);

    void deleteBySessionIdAndFotoId(UUID sessionId, UUID fotoId);
}
