package com.photoizer.crm.financeiro.repository;

import com.photoizer.crm.financeiro.model.Pagamento;
import com.photoizer.crm.shared.model.FormaPagamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface PagamentoRepository extends JpaRepository<Pagamento, UUID> {
    List<Pagamento> findByAgendamentoId(UUID agendamentoId);

    Optional<Pagamento> findByCompraExtraId(UUID compraExtraId);

    Optional<Pagamento> findByAgendamentoIdAndValorAndCompraExtraIdIsNull(UUID agendamentoId, BigDecimal valor);

    @Query("SELECT DISTINCT p.agendamento.id FROM Pagamento p WHERE p.formaPagamento = :forma")
    Set<UUID> findAgendamentoIdsByFormaPagamento(@Param("forma") FormaPagamento forma);
}
