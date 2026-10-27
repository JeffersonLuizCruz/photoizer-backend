package com.photoizer.crm.foto.repository;

import com.photoizer.crm.foto.model.FotoEnsaio;
import com.photoizer.crm.foto.model.StatusFoto;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FotoEnsaioRepository extends JpaRepository<FotoEnsaio, UUID> {

    List<FotoEnsaio> findByAgendamentoIdOrderByOrdemAsc(UUID agendamentoId);

    /**
     * Carrega e bloqueia todas as fotos do agendamento para atribuicao de
     * numeracao. A lock pessimista evita que publicacoes concorrentes gerem
     * o mesmo numero; o indice unico e a segunda linha de defesa.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT f FROM FotoEnsaio f
        WHERE f.agendamentoId = :agendamentoId
        ORDER BY f.ordem ASC, f.id ASC
        """)
    List<FotoEnsaio> findAllByAgendamentoIdForUpdate(@Param("agendamentoId") UUID agendamentoId);

    List<FotoEnsaio> findByAgendamentoIdAndStatusOrderByOrdemAsc(UUID agendamentoId, StatusFoto status);

    int countByAgendamentoId(UUID agendamentoId);

    int countByAgendamentoIdAndStatus(UUID agendamentoId, StatusFoto status);

    @Query("SELECT COUNT(f) FROM FotoEnsaio f WHERE f.agendamentoId = :agendamentoId AND f.selecionadaPacote = true")
    int countSelecionadasPacoteByAgendamentoId(@Param("agendamentoId") UUID agendamentoId);

    @Query("SELECT COUNT(f) FROM FotoEnsaio f WHERE f.agendamentoId = :agendamentoId AND f.status = 'PAGA'")
    int countPagasByAgendamentoId(@Param("agendamentoId") UUID agendamentoId);

    List<FotoEnsaio> findByAgendamentoIdAndCategoria(UUID agendamentoId, String categoria);

    List<FotoEnsaio> findByAgendamentoIdAndTagsContaining(UUID agendamentoId, String tag);

    Optional<FotoEnsaio> findByFotoEdicaoId(UUID fotoEdicaoId);

    @Query("""
        SELECT f FROM FotoEnsaio f
        WHERE f.agendamentoId = :agendamentoId
          AND f.status = 'PUBLICADA'
          AND f.visivel = true
        ORDER BY COALESCE(f.numeroGaleria, 2147483647) ASC, f.ordem ASC
        """)
    List<FotoEnsaio> findPublicadasVisiveisByAgendamentoId(@Param("agendamentoId") UUID agendamentoId);

    List<FotoEnsaio> findByCompraExtraId(UUID compraExtraId);

    /**
     * Fotos extras compradas que ainda não tiveram a versão editada liberada.
     * Alimenta a fila de edição sob demanda no workspace da galeria.
     */
    @Query("""
        SELECT f FROM FotoEnsaio f
        WHERE f.compraExtraId IS NOT NULL
          AND f.status = 'PAGA'
          AND f.versaoAtual <> 'EDITADA'
        ORDER BY f.compraExtraId ASC,
                 COALESCE(f.numeroGaleria, 2147483647) ASC,
                 f.ordem ASC
        """)
    List<FotoEnsaio> findExtrasPendentesEdicao();

    @Query("SELECT COALESCE(MAX(f.ordem), -1) FROM FotoEnsaio f WHERE f.agendamentoId = :agendamentoId")
    int findMaxOrdemByAgendamentoId(@Param("agendamentoId") UUID agendamentoId);

    int countByAgendamentoIdAndArquivosRemovidosTrue(UUID agendamentoId);

    @Query("""
        SELECT MAX(f.arquivosRemovidosEm)
        FROM FotoEnsaio f
        WHERE f.agendamentoId = :agendamentoId AND f.arquivosRemovidos = true
        """)
    java.time.LocalDateTime buscarDataArquivamento(@Param("agendamentoId") UUID agendamentoId);

    @Query("""
        SELECT COALESCE(SUM(f.bytesLiberados), 0)
        FROM FotoEnsaio f
        WHERE f.agendamentoId = :agendamentoId AND f.arquivosRemovidos = true
        """)
    long somarBytesLiberados(@Param("agendamentoId") UUID agendamentoId);

    long countByArquivosRemovidosTrue();

    @Query("""
        SELECT COALESCE(SUM(f.bytesLiberados), 0)
        FROM FotoEnsaio f
        WHERE f.arquivosRemovidos = true
          AND f.arquivosRemovidosEm >= :inicio
          AND f.arquivosRemovidosEm < :fim
        """)
    long somarBytesLiberadosEntre(@Param("inicio") java.time.LocalDateTime inicio,
                                  @Param("fim") java.time.LocalDateTime fim);

    // ─── Agregações para o painel de armazenamento ────────────────────────

    interface ContagemFotos {
        UUID getAgendamentoId();

        long getTotal();

        long getArquivadas();
    }

    interface UltimoDownload {
        UUID getAgendamentoId();

        java.time.LocalDateTime getUltimoDownload();
    }

    interface Arquivamento {
        UUID getAgendamentoId();

        java.time.LocalDateTime getArquivadaEm();

        long getBytesLiberados();
    }

    interface PendenciaFotos {
        UUID getAgendamentoId();

        long getPendencias();
    }

    interface CaminhosFoto {
        UUID getId();

        UUID getAgendamentoId();

        String getOriginalPath();

        String getWatermarkedPath();

        String getThumbPath();

        String getPreviewPath();

        String getEditadaPath();

        String getEditadaWatermarkedPath();

        String getEditadaThumbPath();

        String getEditadaPreviewPath();
    }

    @Query("""
        SELECT f.id AS id,
               f.agendamentoId AS agendamentoId,
               f.originalPath AS originalPath,
               f.watermarkedPath AS watermarkedPath,
               f.thumbPath AS thumbPath,
               f.previewPath AS previewPath,
               f.editadaPath AS editadaPath,
               f.editadaWatermarkedPath AS editadaWatermarkedPath,
               f.editadaThumbPath AS editadaThumbPath,
               f.editadaPreviewPath AS editadaPreviewPath
        FROM FotoEnsaio f
        """)
    List<CaminhosFoto> caminhosDasFotos();

    @Query("""
        SELECT f.agendamentoId AS agendamentoId,
               COUNT(f) AS total,
               SUM(CASE WHEN f.arquivosRemovidos = true THEN 1 ELSE 0 END) AS arquivadas
        FROM FotoEnsaio f
        GROUP BY f.agendamentoId
        """)
    List<ContagemFotos> contarPorAgendamento();

    @Query("""
        SELECT f.agendamentoId AS agendamentoId,
               MAX(f.dataDownload) AS ultimoDownload
        FROM FotoEnsaio f
        GROUP BY f.agendamentoId
        """)
    List<UltimoDownload> ultimoDownloadPorAgendamento();

    @Query("""
        SELECT f.agendamentoId AS agendamentoId,
               MAX(f.arquivosRemovidosEm) AS arquivadaEm,
               COALESCE(SUM(f.bytesLiberados), 0) AS bytesLiberados
        FROM FotoEnsaio f
        WHERE f.arquivosRemovidos = true
        GROUP BY f.agendamentoId
        """)
    List<Arquivamento> arquivamentoPorAgendamento();

    /**
     * Fotos que ainda impedem o expurgo: em seleção ativa ou com compra
     * aguardando comprovante/confirmação.
     */
    @Query("""
        SELECT f.agendamentoId AS agendamentoId,
               COUNT(f) AS pendencias
        FROM FotoEnsaio f
        WHERE f.emSelecaoEdicao = true OR f.status IN :statuses
        GROUP BY f.agendamentoId
        """)
    List<PendenciaFotos> pendenciasPorAgendamento(@Param("statuses") Collection<StatusFoto> statuses);
}
