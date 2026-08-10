package com.photoizer.crm.financeiro.repository;

import com.photoizer.crm.financeiro.model.Receita;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface ReceitaRepository extends JpaRepository<Receita, UUID>, JpaSpecificationExecutor<Receita> {

    List<Receita> findByAgendamentoIdOrderByDataPrevisaoRecebimentoDesc(UUID agendamentoId);
}
