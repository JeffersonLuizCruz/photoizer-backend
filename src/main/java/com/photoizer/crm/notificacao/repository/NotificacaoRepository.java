package com.photoizer.crm.notificacao.repository;

import com.photoizer.crm.notificacao.model.Notificacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificacaoRepository extends JpaRepository<Notificacao, UUID> {

    List<Notificacao> findByUserIdOrderByCreatedAtDesc(UUID userId);

    long countByUserIdAndLidaFalse(UUID userId);
}
