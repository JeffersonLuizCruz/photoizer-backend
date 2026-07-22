package com.photoizer.crm.despesa.repository;

import com.photoizer.crm.despesa.model.Despesa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DespesaRepository extends JpaRepository<Despesa, UUID> {

    List<Despesa> findByDataBetweenOrderByDataDesc(LocalDate inicio, LocalDate fim);

    List<Despesa> findAllByOrderByDataDesc();
}
