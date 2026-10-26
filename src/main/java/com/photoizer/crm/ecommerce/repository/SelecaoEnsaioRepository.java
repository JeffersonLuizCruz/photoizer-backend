package com.photoizer.crm.ecommerce.repository;

import com.photoizer.crm.ecommerce.model.SelecaoEnsaio;
import com.photoizer.crm.ecommerce.model.StatusSelecao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SelecaoEnsaioRepository extends JpaRepository<SelecaoEnsaio, UUID> {

    List<SelecaoEnsaio> findByAgendamentoIdOrderByNumeroDesc(UUID agendamentoId);

    Optional<SelecaoEnsaio> findFirstByAgendamentoIdOrderByNumeroDesc(UUID agendamentoId);

    Optional<SelecaoEnsaio> findFirstByAgendamentoIdAndStatusInOrderByNumeroDesc(
        UUID agendamentoId, Collection<StatusSelecao> statuses);

    List<SelecaoEnsaio> findByStatusInOrderByEnviadaEmAsc(Collection<StatusSelecao> statuses);

    boolean existsByAgendamentoIdAndStatusIn(UUID agendamentoId, Collection<StatusSelecao> statuses);
}
