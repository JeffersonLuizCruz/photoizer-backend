package com.photoizer.crm.foto.service;

import com.photoizer.crm.foto.api.HistoricoArmazenamentoResponse;
import com.photoizer.crm.foto.api.SnapshotArmazenamentoResponse;
import com.photoizer.crm.foto.model.ArmazenamentoSnapshot;
import com.photoizer.crm.foto.repository.ArmazenamentoSnapshotRepository;
import com.photoizer.crm.foto.repository.FotoEnsaioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Histórico diário do consumo de armazenamento.
 *
 * <p>Uma linha por dia (a data é a chave), gravada às 23:55 no fuso do negócio.
 * A coleta manual permite registrar o estado atual sem esperar o job. Não há
 * backfill: a série começa na primeira coleta.
 */
@Service
public class ArmazenamentoHistoricoService {

    private static final Logger log = LoggerFactory.getLogger(ArmazenamentoHistoricoService.class);
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final int MAX_DIAS = 730;

    private final ArmazenamentoSnapshotRepository snapshotRepository;
    private final FotoEnsaioRepository fotoEnsaioRepository;
    private final ArmazenamentoService armazenamentoService;
    private final boolean coletarNoStartup;

    public ArmazenamentoHistoricoService(
            ArmazenamentoSnapshotRepository snapshotRepository,
            FotoEnsaioRepository fotoEnsaioRepository,
            ArmazenamentoService armazenamentoService,
            @Value("${app.storage.historico.coletar-no-startup:true}") boolean coletarNoStartup) {
        this.snapshotRepository = snapshotRepository;
        this.fotoEnsaioRepository = fotoEnsaioRepository;
        this.armazenamentoService = armazenamentoService;
        this.coletarNoStartup = coletarNoStartup;
    }

    @Scheduled(cron = "${app.storage.historico.cron:0 55 23 * * *}", zone = "America/Sao_Paulo")
    @Transactional
    public void coletarDiario() {
        coletarHoje();
    }

    /** Coleta manual do dia corrente (usada pelo endpoint e pelo job). */
    @Transactional
    public SnapshotArmazenamentoResponse coletarHoje() {
        return coletar(LocalDate.now(ZONE));
    }

    /**
     * Garante um ponto no dia do deploy. É idempotente e não impede o boot:
     * qualquer falha é logada e o próximo job tenta novamente.
     *
     * <p>Não usa {@code @Transactional} de propósito: se a transação fosse
     * marcada como rollback-only, o commit falharia fora do try/catch e poderia
     * derrubar o startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void coletarNoStartup() {
        if (!coletarNoStartup) {
            return;
        }
        try {
            var hoje = LocalDate.now(ZONE);
            if (snapshotRepository.existsById(hoje)) {
                return;
            }
            coletar(hoje);
        } catch (RuntimeException e) {
            log.atWarn()
                .addKeyValue("event_action", "armazenamento.historico_startup_falhou")
                .setCause(e)
                .log("Não foi possível coletar o histórico de armazenamento no startup");
        }
    }

    /**
     * Grava (ou atualiza) o snapshot da data informada.
     */
    @Transactional
    public SnapshotArmazenamentoResponse coletar(LocalDate data) {
        var resumo = armazenamentoService.resumo();
        var snapshot = snapshotRepository.findById(data)
            .orElseGet(() -> ArmazenamentoSnapshot.builder().data(data).build());

        snapshot.setBytesFotos(resumo.bytesFotos());
        snapshot.setBytesDerivados(resumo.bytesDerivados());
        snapshot.setBytesEditadas(resumo.bytesEditadas());
        snapshot.setBytesDocumentos(resumo.bytesDocumentos());
        snapshot.setBytesPropostas(resumo.bytesPropostas());
        snapshot.setBytesOutros(resumo.bytesOutros());
        snapshot.setBytesTotal(resumo.bytesTotal());
        snapshot.setVolumeTotal(resumo.volumeTotal());
        snapshot.setVolumeLivre(resumo.volumeLivre());
        snapshot.setTotalFotos((int) resumo.totalFotos());
        snapshot.setTotalGalerias(resumo.totalGalerias());
        snapshot.setGaleriasArquivadas(resumo.galeriasArquivadas());
        snapshot.setFotosArquivadas((int) fotoEnsaioRepository.countByArquivosRemovidosTrue());

        var inicioDoDia = data.atStartOfDay();
        snapshot.setBytesLiberadosNoDia(
            fotoEnsaioRepository.somarBytesLiberadosEntre(inicioDoDia, inicioDoDia.plusDays(1)));
        snapshot.setColetadoEm(LocalDateTime.now(ZONE));

        var salvo = snapshotRepository.save(snapshot);
        log.atInfo()
            .addKeyValue("event_action", "armazenamento.historico_coletado")
            .addKeyValue("data", data)
            .addKeyValue("bytes_total", salvo.getBytesTotal())
            .addKeyValue("bytes_liberados", salvo.getBytesLiberadosNoDia())
            .log("Snapshot de armazenamento coletado");
        return toResponse(salvo);
    }

    @Transactional(readOnly = true)
    public HistoricoArmazenamentoResponse listar(int dias) {
        var periodo = Math.max(1, Math.min(dias, MAX_DIAS));
        var inicio = LocalDate.now(ZONE).minusDays(periodo - 1L);
        var snapshots = snapshotRepository.findByDataGreaterThanEqualOrderByDataAsc(inicio).stream()
            .map(this::toResponse)
            .toList();
        return new HistoricoArmazenamentoResponse(periodo, snapshots);
    }

    private SnapshotArmazenamentoResponse toResponse(ArmazenamentoSnapshot snapshot) {
        return new SnapshotArmazenamentoResponse(
            snapshot.getData(),
            snapshot.getBytesFotos(),
            snapshot.getBytesDerivados(),
            snapshot.getBytesEditadas(),
            snapshot.getBytesDocumentos(),
            snapshot.getBytesPropostas(),
            snapshot.getBytesOutros(),
            snapshot.getBytesTotal(),
            snapshot.getVolumeTotal(),
            snapshot.getVolumeLivre(),
            snapshot.getTotalFotos(),
            snapshot.getTotalGalerias(),
            snapshot.getGaleriasArquivadas(),
            snapshot.getFotosArquivadas(),
            snapshot.getBytesLiberadosNoDia(),
            snapshot.getColetadoEm()
        );
    }
}
