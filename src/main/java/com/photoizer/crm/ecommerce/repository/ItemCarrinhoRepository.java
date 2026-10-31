package com.photoizer.crm.ecommerce.repository;

import com.photoizer.crm.ecommerce.model.ItemCarrinho;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ItemCarrinhoRepository extends JpaRepository<ItemCarrinho, UUID> {

    List<ItemCarrinho> findBySessionIdAndAgendamentoIdOrderByAuditInfoCreatedAtAsc(UUID sessionId, UUID agendamentoId);

    /**
     * Serializa adições concorrentes no mesmo carrinho. O índice único
     * (V24) é a segunda linha de defesa.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT i FROM ItemCarrinho i
        WHERE i.sessionId = :sessionId AND i.agendamentoId = :agendamentoId
        """)
    List<ItemCarrinho> findBySessaoAndAgendamentoForUpdate(
        @Param("sessionId") UUID sessionId,
        @Param("agendamentoId") UUID agendamentoId);

    void deleteBySessionIdAndAgendamentoIdAndFotoId(UUID sessionId, UUID agendamentoId, UUID fotoId);

    void deleteByFotoIdIn(Collection<UUID> fotoIds);

    int countBySessionIdAndAgendamentoId(UUID sessionId, UUID agendamentoId);

    void deleteBySessionIdAndAgendamentoId(UUID sessionId, UUID agendamentoId);
}
