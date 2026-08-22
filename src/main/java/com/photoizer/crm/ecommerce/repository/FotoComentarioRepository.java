package com.photoizer.crm.ecommerce.repository;

import com.photoizer.crm.ecommerce.model.FotoComentario;
import com.photoizer.crm.ecommerce.model.OrigemComentario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FotoComentarioRepository extends JpaRepository<FotoComentario, UUID> {

    List<FotoComentario> findByFotoIdOrderByAuditInfoCreatedAtAsc(UUID fotoId);

    List<FotoComentario> findByAgendamentoIdOrderByAuditInfoCreatedAtAsc(UUID agendamentoId);

    long countByFotoIdAndOrigemAndLidaFalse(UUID fotoId, OrigemComentario origem);

    long countByAgendamentoIdAndOrigemAndLidaFalse(UUID agendamentoId, OrigemComentario origem);
}