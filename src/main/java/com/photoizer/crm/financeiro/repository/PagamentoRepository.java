package com.photoizer.crm.financeiro.repository;

import com.photoizer.crm.financeiro.model.Pagamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PagamentoRepository extends JpaRepository<Pagamento, UUID> {
    List<Pagamento> findByAgendamentoId(UUID agendamentoId);
}
