package com.photoizer.crm.comissao.repository;

import com.photoizer.crm.comissao.model.Indicacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IndicacaoRepository extends JpaRepository<Indicacao, UUID> {

    List<Indicacao> findAllByAgendamentoId(UUID agendamentoId);

    List<Indicacao> findByIndicadorId(UUID indicadorId);

    List<Indicacao> findByIndicadorTelefoneOrderByCreatedAtDesc(String indicadorTelefone);

    @Query("SELECT i FROM Indicacao i WHERE i.agendamentoId IN :ids")
    List<Indicacao> findByAgendamentoIdIn(@Param("ids") List<UUID> ids);

    @Query("SELECT DISTINCT i.indicadorTelefone FROM Indicacao i")
    List<String> findAllDistinctTelefones();
}
