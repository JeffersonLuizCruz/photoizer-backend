package com.photoizer.crm.financeiro.repository;

import com.photoizer.crm.financeiro.model.ExtraServico;
import com.photoizer.crm.financeiro.model.TipoExtra;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExtraServicoRepository extends JpaRepository<ExtraServico, UUID> {
    List<ExtraServico> findByAgendamentoId(UUID agendamentoId);
    List<ExtraServico> findByAgendamentoIdAndTipo(UUID agendamentoId, TipoExtra tipo);
}
