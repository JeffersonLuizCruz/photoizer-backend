package com.photoizer.crm.ecommerce.repository;

import com.photoizer.crm.ecommerce.model.ItemCarrinho;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ItemCarrinhoRepository extends JpaRepository<ItemCarrinho, UUID> {

    List<ItemCarrinho> findBySessionIdAndAgendamentoIdOrderByCreatedAtAsc(UUID sessionId, UUID agendamentoId);

    void deleteBySessionIdAndAgendamentoIdAndFotoId(UUID sessionId, UUID agendamentoId, UUID fotoId);

    int countBySessionIdAndAgendamentoId(UUID sessionId, UUID agendamentoId);

    void deleteBySessionIdAndAgendamentoId(UUID sessionId, UUID agendamentoId);
}
