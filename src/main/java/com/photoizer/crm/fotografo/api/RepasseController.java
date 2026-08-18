package com.photoizer.crm.fotografo.api;

import com.photoizer.crm.agenda.model.AgendamentoFotografo;
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

@RestController
@RequestMapping("/api/v1/repasses")
@Tag(name = "Repasses", description = "Gestão de repasses pendentes para fotógrafos")
public class RepasseController {

    private final AgendamentoFotografoService agendamentoFotografoService;

    public RepasseController(AgendamentoFotografoService agendamentoFotografoService) {
        this.agendamentoFotografoService = agendamentoFotografoService;
    }

    @GetMapping("/pendentes")
    @Operation(summary = "Listar todos os repasses pendentes (admin)")
    public ResponseEntity<List<AgendamentoFotografo>> listarPendentes(
            @RequestParam(required = false) UUID fotografoId) {
        if (fotografoId != null) {
            return ResponseEntity.ok(agendamentoFotografoService.listarPendentesPorFotografo(fotografoId));
        }
        return ResponseEntity.ok(agendamentoFotografoService.listarPendentes());
    }

    @PostMapping("/pagar-lote")
    @Operation(summary = "Pagar múltiplos repasses em lote")
    public ResponseEntity<List<AgendamentoFotografo>> pagarLote(@RequestBody List<UUID> ids) {
        return ResponseEntity.ok(agendamentoFotografoService.pagarRepasseLote(ids));
    }
}
