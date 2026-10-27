package com.photoizer.crm.agenda.repository;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgendamentoRepository extends JpaRepository<Agendamento, UUID>, JpaSpecificationExecutor<Agendamento> {

    interface CaminhosArquivo {
        String getUrlComprovanteEntrada();

        String getUrlComprovanteFinal();

        String getUrlPdfAssinatura();

        String getUrlAssinaturaImagem();
    }

    @Query("""
        SELECT a.urlComprovanteEntrada AS urlComprovanteEntrada,
               a.urlComprovanteFinal AS urlComprovanteFinal,
               a.urlPdfAssinatura AS urlPdfAssinatura,
               a.urlAssinaturaImagem AS urlAssinaturaImagem
        FROM Agendamento a
        WHERE a.urlComprovanteEntrada IS NOT NULL
           OR a.urlComprovanteFinal IS NOT NULL
           OR a.urlPdfAssinatura IS NOT NULL
           OR a.urlAssinaturaImagem IS NOT NULL
        """)
    List<CaminhosArquivo> caminhosDeArquivo();

    /**
     * Lista todos os agendamentos com o cliente carregado, para o painel de
     * armazenamento (evita N+1 ao montar a lista de galerias).
     */
    @EntityGraph(attributePaths = "cliente")
    @Query("SELECT a FROM Agendamento a")
    List<Agendamento> findAllComCliente();

    boolean existsByDataHoraEnsaioBetweenAndStatusNotIn(
        LocalDateTime start, LocalDateTime end, List<StatusAgendamento> statusesIgnorados);

    boolean existsByDataHoraEnsaioBetweenAndStatusNotInAndIdNot(
        LocalDateTime start, LocalDateTime end, List<StatusAgendamento> statusesIgnorados, UUID idExcluded);

    @Query("SELECT a FROM Agendamento a WHERE a.localEnsaio = :local " +
           "AND a.dataHoraEnsaio >= :diaInicio AND a.dataHoraEnsaio < :diaFim " +
           "AND a.status NOT IN :statusesIgnorados")
    List<Agendamento> findActiveByLocalAndDataBetween(
        @Param("local") String local,
        @Param("diaInicio") LocalDateTime diaInicio,
        @Param("diaFim") LocalDateTime diaFim,
        @Param("statusesIgnorados") List<StatusAgendamento> statusesIgnorados);

    @Query("SELECT a FROM Agendamento a WHERE a.dataHoraEnsaio >= :diaInicio AND a.dataHoraEnsaio < :diaFim " +
           "AND a.status NOT IN :statusesIgnorados")
    List<Agendamento> findByDataBetween(
        @Param("diaInicio") LocalDateTime diaInicio,
        @Param("diaFim") LocalDateTime diaFim,
        @Param("statusesIgnorados") List<StatusAgendamento> statusesIgnorados);

    @Query("SELECT a FROM Agendamento a WHERE a.dataHoraEnsaio >= :diaInicio AND a.dataHoraEnsaio < :diaFim " +
           "AND a.status NOT IN :statusesIgnorados AND a.id <> :excluirId")
    List<Agendamento> findActiveBetweenExcludingId(
        @Param("diaInicio") LocalDateTime diaInicio,
        @Param("diaFim") LocalDateTime diaFim,
        @Param("statusesIgnorados") List<StatusAgendamento> statusesIgnorados,
        @Param("excluirId") UUID excluirId);

    @Query("SELECT a FROM Agendamento a WHERE a.dataHoraEnsaio < :limite " +
           "AND a.valorRestante > 0 AND a.status NOT IN :statusesIgnorados")
    List<Agendamento> findInadimplentes(
        @Param("limite") LocalDateTime limite,
        @Param("statusesIgnorados") List<StatusAgendamento> statusesIgnorados);

    @Query("SELECT a FROM Agendamento a WHERE a.fotografo.id = :fotografoId " +
           "AND a.dataHoraEnsaio >= :diaInicio AND a.dataHoraEnsaio < :diaFim " +
           "AND a.status NOT IN :statusesIgnorados")
    List<Agendamento> findActiveByFotografoAndDataBetween(
        @Param("fotografoId") UUID fotografoId,
        @Param("diaInicio") LocalDateTime diaInicio,
        @Param("diaFim") LocalDateTime diaFim,
        @Param("statusesIgnorados") List<StatusAgendamento> statusesIgnorados);

    @Query("SELECT a FROM Agendamento a WHERE a.fotografo.id = :fotografoId " +
           "AND a.dataHoraEnsaio >= :diaInicio AND a.dataHoraEnsaio < :diaFim " +
           "AND a.status NOT IN :statusesIgnorados AND a.id <> :excluirId")
    List<Agendamento> findActiveByFotografoAndDataBetweenExcludingId(
        @Param("fotografoId") UUID fotografoId,
        @Param("diaInicio") LocalDateTime diaInicio,
        @Param("diaFim") LocalDateTime diaFim,
        @Param("statusesIgnorados") List<StatusAgendamento> statusesIgnorados,
        @Param("excluirId") UUID excluirId);

    boolean existsByFotografoIdAndDataHoraEnsaioBetweenAndStatusNotIn(
        UUID fotografoId, LocalDateTime start, LocalDateTime end, List<StatusAgendamento> statusesIgnorados);

    boolean existsByFotografoIdAndDataHoraEnsaioBetweenAndStatusNotInAndIdNot(
        UUID fotografoId, LocalDateTime start, LocalDateTime end, List<StatusAgendamento> statusesIgnorados, UUID idExcluded);

    @Query("SELECT a FROM Agendamento a WHERE a.cliente.id = :clienteId ORDER BY a.dataHoraEnsaio DESC")
    List<Agendamento> findByClienteId(@Param("clienteId") UUID clienteId);

    @EntityGraph(attributePaths = {"cliente", "pacote"})
    java.util.Optional<Agendamento> findByTokenGaleria(UUID tokenGaleria);

    @Query("SELECT a FROM Agendamento a JOIN FETCH a.pacote LEFT JOIN FETCH a.editor WHERE a.tokenPropostaHash = :hash")
    Optional<Agendamento> findByTokenPropostaHash(@Param("hash") String hash);

    long countByDataHoraEnsaioBetween(LocalDateTime start, LocalDateTime end);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Agendamento a WHERE a.id = :id")
    Optional<Agendamento> findByIdWithLock(@Param("id") UUID id);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM Agendamento a " +
           "WHERE a.cliente.id = :clienteId AND a.valorRestante > 0 AND a.status <> com.photoizer.crm.agenda.model.StatusAgendamento.CANCELADO")
    boolean existsByClienteIdWithSaldoDevedor(@Param("clienteId") UUID clienteId);

    boolean existsByPacoteIdAndStatusNot(UUID pacoteId, StatusAgendamento status);

    long countByPacoteIdAndStatusNot(UUID pacoteId, StatusAgendamento status);

    @Query("SELECT a FROM Agendamento a JOIN FETCH a.pacote p " +
           "WHERE a.fotografo.id = :fotografoId " +
           "AND a.dataHoraEnsaio >= :diaInicio AND a.dataHoraEnsaio < :diaFim " +
           "AND a.status NOT IN :statusesIgnorados " +
           "AND p.bloqueiaDiaInteiro = true")
    List<Agendamento> findBloqueiaDiaInteiroByFotografoAndDataBetween(
        @Param("fotografoId") UUID fotografoId,
        @Param("diaInicio") LocalDateTime diaInicio,
        @Param("diaFim") LocalDateTime diaFim,
        @Param("statusesIgnorados") List<StatusAgendamento> statusesIgnorados);

    @Query("SELECT a FROM Agendamento a JOIN FETCH a.cliente LEFT JOIN FETCH a.pacote " +
           "WHERE a.fotografo.id = :fotografoId ORDER BY a.dataHoraEnsaio DESC")
    List<Agendamento> findByFotografoIdWithDetails(@Param("fotografoId") UUID fotografoId);

    @Query("SELECT a FROM Agendamento a WHERE a.status NOT IN :statusesIgnorados " +
           "AND a.dataHoraEnsaio < :fim AND a.dataHoraEnsaio >= :inicioMenosBuffer " +
           "ORDER BY a.dataHoraEnsaio")
    List<Agendamento> findAtivosParaConflitoNoPeriodo(
        @Param("inicioMenosBuffer") LocalDateTime inicioMenosBuffer,
        @Param("fim") LocalDateTime fim,
        @Param("statusesIgnorados") List<StatusAgendamento> statusesIgnorados);

    @Query("SELECT a FROM Agendamento a WHERE a.status NOT IN :statusesIgnorados " +
           "AND a.fotografo.id = :fotografoId " +
           "AND a.dataHoraEnsaio < :fim AND a.dataHoraEnsaio >= :inicioMenosBuffer " +
           "ORDER BY a.dataHoraEnsaio")
    List<Agendamento> findAtivosParaConflitoNoPeriodoByFotografo(
        @Param("fotografoId") UUID fotografoId,
        @Param("inicioMenosBuffer") LocalDateTime inicioMenosBuffer,
        @Param("fim") LocalDateTime fim,
        @Param("statusesIgnorados") List<StatusAgendamento> statusesIgnorados);

    /**
     * Ensaios candidatos a aparecer na Central de Galerias. Inclui todo o
     * trabalho em aberto (independente da idade) e os ensaios finalizados
     * dentro da janela historica consultada. Cliente e pacote vem no mesmo
     * select para montar a fila sem N+1.
     */
    @EntityGraph(attributePaths = {"cliente", "pacote"})
    @Query("""
        SELECT a FROM Agendamento a
        WHERE a.status IN :statuses
          AND (a.status <> com.photoizer.crm.agenda.model.StatusAgendamento.FINALIZADO
               OR a.dataHoraEnsaio >= :desde)
        ORDER BY a.dataHoraEnsaio DESC
        """)
    List<Agendamento> findParaPipeline(
        @Param("statuses") Collection<StatusAgendamento> statuses,
        @Param("desde") LocalDateTime desde);

    /**
     * Ensaios do periodo usado nos indicadores de gestao (SLA e funil).
     * Inclui os que ainda nao entraram na fila (realizados/aguardando pagamento
     * final) para que a taxa de publicacao no periodo seja fiel.
     */
    @EntityGraph(attributePaths = {"cliente", "pacote"})
    @Query("""
        SELECT a FROM Agendamento a
        WHERE a.status IN :statuses
          AND a.dataHoraEnsaio >= :desde
        ORDER BY a.dataHoraEnsaio ASC
        """)
    List<Agendamento> findParaDesempenho(
        @Param("statuses") Collection<StatusAgendamento> statuses,
        @Param("desde") LocalDateTime desde);

}
