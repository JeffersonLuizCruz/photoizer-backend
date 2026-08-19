package com.photoizer.crm.agenda.api;

import com.photoizer.crm.agenda.model.AgendamentoFotografo;
import com.photoizer.crm.shared.model.TipoRepasse;
import com.photoizer.crm.agenda.service.AgendamentoFotografoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agendamentos/{agendamentoId}/fotografos")
@Tag(name = "Fotógrafos do Agendamento", description = "Gerencia os fotógrafos vinculados a um ensaio")
public class AgendamentoFotografoController {

    private final AgendamentoFotografoService service;

    public AgendamentoFotografoController(AgendamentoFotografoService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Listar fotógrafos do agendamento")
    public ResponseEntity<List<AgendamentoFotografo>> listar(@PathVariable UUID agendamentoId) {
        return ResponseEntity.ok(service.listarFotografos(agendamentoId));
    }

    @PostMapping
    @Operation(summary = "Adicionar parceiro ao agendamento", description = "Valor pode ser FIXO (R$) ou PERCENTUAL (%)")
    public ResponseEntity<AgendamentoFotografo> adicionar(
            @PathVariable UUID agendamentoId,
            @RequestBody AdicionarFotografoRequest request) {
        return ResponseEntity.ok(service.adicionarFotografo(
            agendamentoId, request.fotografoId(), request.valorRepassar(),
            request.tipoValor(), request.percentual()));
    }

    @PutMapping("/{fotografoId}")
    @Operation(summary = "Atualizar repasse do parceiro")
    public ResponseEntity<AgendamentoFotografo> atualizarRepasse(
            @PathVariable UUID agendamentoId,
            @PathVariable UUID fotografoId,
            @RequestBody AtualizarRepasseRequest request) {
        return ResponseEntity.ok(service.atualizarRepasse(
            agendamentoId, fotografoId, request.valorRepassar(),
            request.tipoValor(), request.percentual()));
    }

    @DeleteMapping("/{fotografoId}")
    @Operation(summary = "Remover parceiro do agendamento")
    public ResponseEntity<Void> remover(@PathVariable UUID agendamentoId, @PathVariable UUID fotografoId) {
        service.removerFotografo(agendamentoId, fotografoId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{fotografoId}/pagar")
    @Operation(summary = "Marcar repasse como pago")
    public ResponseEntity<AgendamentoFotografo> pagarRepasse(
            @PathVariable UUID agendamentoId, @PathVariable UUID fotografoId) {
        return ResponseEntity.ok(service.pagarRepasse(agendamentoId, fotografoId));
    }

    @PatchMapping("/{fotografoId}/cancelar")
    @Operation(summary = "Cancelar repasse")
    public ResponseEntity<AgendamentoFotografo> cancelarRepasse(
            @PathVariable UUID agendamentoId, @PathVariable UUID fotografoId) {
        return ResponseEntity.ok(service.cancelarRepasse(agendamentoId, fotografoId));
    }

    public record AdicionarFotografoRequest(
        @NotNull UUID fotografoId,
        BigDecimal valorRepassar,
        TipoRepasse tipoValor,
        BigDecimal percentual
    ) {
        public AdicionarFotografoRequest(UUID fotografoId, BigDecimal valorRepassar) {
            this(fotografoId, valorRepassar, TipoRepasse.FIXO, null);
        }
    }

    public record AtualizarRepasseRequest(
        BigDecimal valorRepassar,
        TipoRepasse tipoValor,
        BigDecimal percentual
    ) {
        public AtualizarRepasseRequest(BigDecimal valorRepassar) {
            this(valorRepassar, null, null);
        }
    }
}
