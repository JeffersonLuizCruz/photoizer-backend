package com.photoizer.crm.despesa.repository;

import com.photoizer.crm.despesa.model.Despesa;
import com.photoizer.crm.despesa.model.StatusDespesa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DespesaRepository extends JpaRepository<Despesa, UUID>, JpaSpecificationExecutor<Despesa> {

    List<Despesa> findByDataBetweenOrderByDataDesc(LocalDate inicio, LocalDate fim);

    List<Despesa> findAllByOrderByDataDesc();

    List<Despesa> findByAgendamentoIdOrderByDataDesc(UUID agendamentoId);

    List<Despesa> findByStatusAndDataProximaGeracaoNotNullAndDataProximaGeracaoLessThanEqual(
        StatusDespesa status, LocalDate dataLimite);

    long countByCategoriaRefId(UUID categoriaId);

    List<Despesa> findByFotografoIdOrderByDataDesc(UUID fotografoId);
}
