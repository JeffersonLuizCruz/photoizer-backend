package com.photoizer.crm.agenda.api;

import com.photoizer.crm.agenda.service.AgendamentoFotografoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Controller para gestão de repasses de fotógrafos.
 *
 * Design Pattern: Facade — opera sobre a entidade AgendamentoFotografo
 * (que é de domínio do agenda) sem depender de outros módulos.
 *
 * Migração: movido do módulo fotografo para agenda (Decisão: repasse é
 * operação de domínio do agenda, não do fotografo). O módulo fotografo
 * mantém apenas relatórios que consomem dados de repasses.
 */
@RestController
@RequestMapping("/api/v1/repasses")
@Tag(name = "Repasses", description = "Gestão de repasses pendentes para fotógrafos")
public class RepasseController {

    private final AgendamentoFotografoService agendamentoFotografoService;
    private final RepasseMapper repasseMapper;

    public RepasseController(AgendamentoFotografoService agendamentoFotografoService,
                             RepasseMapper repasseMapper) {
        this.agendamentoFotografoService = agendamentoFotografoService;
        this.repasseMapper = repasseMapper;
    }

    @GetMapping("/pendentes")
    @Operation(summary = "Listar todos os repasses pendentes (admin)")
    public ResponseEntity<List<RepasseResponse>> listarPendentes(
            @RequestParam(required = false) UUID fotografoId) {
        List<com.photoizer.crm.agenda.model.AgendamentoFotografo> repasses;
        if (fotografoId != null) {
            repasses = agendamentoFotografoService.listarPendentesPorFotografo(fotografoId);
        } else {
            repasses = agendamentoFotografoService.listarPendentes();
        }
        var responses = repasses.stream()
            .map(repasseMapper::toCompatibleResponse)
            .toList();
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/pagar-lote")
    @Operation(summary = "Pagar múltiplos repasses em lote")
    public ResponseEntity<List<RepasseResponse>> pagarLote(@RequestBody List<UUID> ids) {
        var repasses = agendamentoFotografoService.pagarRepasseLote(ids);
        var responses = repasses.stream()
            .map(repasseMapper::toCompatibleResponse)
            .toList();
        return ResponseEntity.ok(responses);
    }
}
