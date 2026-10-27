package com.photoizer.crm.ecommerce.repository;

import com.photoizer.crm.ecommerce.model.SelecaoEnsaio;
import com.photoizer.crm.ecommerce.model.StatusSelecao;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SelecaoEnsaioRepository extends JpaRepository<SelecaoEnsaio, UUID> {

    /**
     * Serializa a inclusão de extras com a publicação da edição: sem o lock,
     * um extra poderia entrar no snapshot depois da checagem de "tudo editado".
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SelecaoEnsaio s WHERE s.id = :id")
    Optional<SelecaoEnsaio> findByIdForUpdate(@Param("id") UUID id);

    List<SelecaoEnsaio> findByAgendamentoIdOrderByNumeroDesc(UUID agendamentoId);

    Optional<SelecaoEnsaio> findFirstByAgendamentoIdOrderByNumeroDesc(UUID agendamentoId);

    Optional<SelecaoEnsaio> findFirstByAgendamentoIdAndStatusInOrderByNumeroDesc(
        UUID agendamentoId, Collection<StatusSelecao> statuses);

    List<SelecaoEnsaio> findByStatusInOrderByEnviadaEmAsc(Collection<StatusSelecao> statuses);

    boolean existsByAgendamentoIdAndStatusIn(UUID agendamentoId, Collection<StatusSelecao> statuses);

    /**
     * Ultimas selecoes relevantes de varias galerias (ativa ou publicada).
     * Ordenadas por numero desc para que o primeiro item de cada agendamento
     * seja a selecao mais recente.
     */
    List<SelecaoEnsaio> findByAgendamentoIdInAndStatusInOrderByNumeroDesc(
        Collection<UUID> agendamentoIds, Collection<StatusSelecao> statuses);
}
