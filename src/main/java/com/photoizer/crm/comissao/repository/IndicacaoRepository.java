package com.photoizer.crm.comissao.repository;

import com.photoizer.crm.comissao.model.Indicacao;
import com.photoizer.crm.comissao.repository.projection.IndicadorComissaoProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface IndicacaoRepository extends JpaRepository<Indicacao, UUID> {

    List<Indicacao> findAllByAgendamentoId(UUID agendamentoId);

    List<Indicacao> findByIndicadorId(UUID indicadorId);

    List<Indicacao> findByIndicadorTelefoneOrderByAuditInfoCreatedAtDesc(String indicadorTelefone);

    @Query("SELECT i FROM Indicacao i WHERE i.agendamentoId IN :ids")
    List<Indicacao> findByAgendamentoIdIn(@Param("ids") List<UUID> ids);

    /**
     * Query agregada que substitui o N+1 do controller.
     * Uma única query com GROUP BY retorna telefone, nome, id do indicador,
     * totais por status e contagem — eliminando o loop de soma em memória.
     */
    @Query("""
        SELECT i.indicadorTelefone AS telefone,
               i.indicadorNome AS nome,
               i.indicadorId AS indicadorId,
               SUM(CASE WHEN i.status = com.photoizer.crm.comissao.model.StatusIndicacao.PENDENTE
                        THEN i.valorComissao ELSE 0 END) AS totalPendente,
               SUM(CASE WHEN i.status = com.photoizer.crm.comissao.model.StatusIndicacao.PAGA
                        THEN i.valorComissao ELSE 0 END) AS totalPago,
               SUM(CASE WHEN i.status = com.photoizer.crm.comissao.model.StatusIndicacao.CANCELADA
                        THEN i.valorComissao ELSE 0 END) AS totalCancelado,
               COUNT(i) AS totalIndicacoes
        FROM Indicacao i
        GROUP BY i.indicadorTelefone, i.indicadorNome, i.indicadorId
        """)
    List<IndicadorComissaoProjection> findIndicadoresComResumo();

    @Query("SELECT DISTINCT i.indicadorTelefone FROM Indicacao i")
    List<String> findAllDistinctTelefones();
}
