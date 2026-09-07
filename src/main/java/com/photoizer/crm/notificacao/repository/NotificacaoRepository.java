package com.photoizer.crm.notificacao.repository;

import com.photoizer.crm.notificacao.model.Notificacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface NotificacaoRepository extends JpaRepository<Notificacao, UUID> {

    Page<Notificacao> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndLidaFalse(UUID userId);

    @Modifying
    @Query("UPDATE Notificacao n SET n.lida = true WHERE n.userId = :userId AND n.lida = false")
    int marcarTodasComoLidas(@Param("userId") UUID userId);

    void deleteByUserId(UUID userId);
}
