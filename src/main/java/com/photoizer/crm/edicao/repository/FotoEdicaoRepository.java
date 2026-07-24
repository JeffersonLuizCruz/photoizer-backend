package com.photoizer.crm.edicao.repository;

import com.photoizer.crm.edicao.model.FotoEdicao;
import com.photoizer.crm.edicao.model.StatusFotoEdicao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FotoEdicaoRepository extends JpaRepository<FotoEdicao, UUID> {

    List<FotoEdicao> findByEdicaoIdOrderByOrdemAsc(UUID edicaoId);

    int countByEdicaoId(UUID edicaoId);

    int countByEdicaoIdAndStatus(UUID edicaoId, StatusFotoEdicao status);

    List<FotoEdicao> findByEdicaoIdAndStatus(UUID edicaoId, StatusFotoEdicao status);
}
