package com.photoizer.crm.agenda.api;

import com.photoizer.crm.agenda.model.AgendamentoFotografo;
import com.photoizer.crm.shared.model.TipoRepasse;
import com.photoizer.crm.agenda.service.AgendamentoFotografoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
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
@RolesAllowed({"ADMIN", "FOTOGRAFO", "EDITOR"})
@Tag(name = "Fotógrafos do Agendamento", description = "Fotógrafos vinculados a um ensaio (repasses por ensaio).")
public class AgendamentoFotografoController {

    private final AgendamentoFotografoService service;

    public AgendamentoFotografoController(AgendamentoFotografoService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(operationId = "listarFotografosDoAgendamento", summary = "Listar fotógrafos do agendamento")
    public ResponseEntity<List<AgendamentoFotografo>> listar(
            @PathVariable @Parameter(description = "ID do agendamento") UUID agendamentoId) {
        return ResponseEntity.ok(service.listarFotografos(agendamentoId));
    }

    @PostMapping
    @Operation(operationId = "adicionarFotografoAoAgendamento", summary = "Adicionar parceiro ao agendamento",
        description = "Valor pode ser FIXO (R$) ou PERCENTUAL (%)")
    public ResponseEntity<AgendamentoFotografo> adicionar(
            @PathVariable @Parameter(description = "ID do agendamento") UUID agendamentoId,
            @RequestBody AdicionarFotografoRequest request) {
        return ResponseEntity.ok(service.adicionarFotografo(
            agendamentoId, request.fotografoId(), request.valorRepassar(),
            request.tipoValor(), request.percentual()));
    }

    @PutMapping("/{fotografoId}")
    @Operation(operationId = "atualizarRepasseDoAgendamento", summary = "Atualizar repasse do parceiro")
    public ResponseEntity<AgendamentoFotografo> atualizarRepasse(
            @PathVariable @Parameter(description = "ID do agendamento") UUID agendamentoId,
            @PathVariable @Parameter(description = "ID do profissional") UUID fotografoId,
            @RequestBody AtualizarRepasseRequest request) {
        return ResponseEntity.ok(service.atualizarRepasse(
            agendamentoId, fotografoId, request.valorRepassar(),
            request.tipoValor(), request.percentual()));
    }

    @DeleteMapping("/{fotografoId}")
    @Operation(operationId = "removerFotografoDoAgendamento", summary = "Remover parceiro do agendamento")
    public ResponseEntity<Void> remover(
            @PathVariable @Parameter(description = "ID do agendamento") UUID agendamentoId,
            @PathVariable @Parameter(description = "ID do profissional") UUID fotografoId) {
        service.removerFotografo(agendamentoId, fotografoId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{fotografoId}/pagar")
    @Operation(operationId = "pagarRepasseDoAgendamento", summary = "Marcar repasse como pago")
    public ResponseEntity<AgendamentoFotografo> pagarRepasse(
            @PathVariable @Parameter(description = "ID do agendamento") UUID agendamentoId,
            @PathVariable @Parameter(description = "ID do profissional") UUID fotografoId) {
        return ResponseEntity.ok(service.pagarRepasse(agendamentoId, fotografoId));
    }

    @PatchMapping("/{fotografoId}/cancelar")
    @Operation(operationId = "cancelarRepasseDoAgendamento", summary = "Cancelar repasse")
    public ResponseEntity<AgendamentoFotografo> cancelarRepasse(
            @PathVariable @Parameter(description = "ID do agendamento") UUID agendamentoId,
            @PathVariable @Parameter(description = "ID do profissional") UUID fotografoId) {
        return ResponseEntity.ok(service.cancelarRepasse(agendamentoId, fotografoId));
    }

    @Schema(name = "AdicionarFotografoRequest", description = "Vínculo de um parceiro a um ensaio.")
    public record AdicionarFotografoRequest(
        @Schema(description = "ID do profissional") @NotNull UUID fotografoId,
        @Schema(description = "Valor a repassar", nullable = true) BigDecimal valorRepassar,
        @Schema(description = "Tipo do valor (FIXO/PERCENTUAL)", nullable = true) TipoRepasse tipoValor,
        @Schema(description = "Percentual", nullable = true) BigDecimal percentual
    ) {
        public AdicionarFotografoRequest(UUID fotografoId, BigDecimal valorRepassar) {
            this(fotografoId, valorRepassar, TipoRepasse.FIXO, null);
        }
    }

    @Schema(name = "AtualizarRepasseRequest", description = "Atualização do repasse de um parceiro.")
    public record AtualizarRepasseRequest(
        @Schema(description = "Valor a repassar", nullable = true) BigDecimal valorRepassar,
        @Schema(description = "Tipo do valor (FIXO/PERCENTUAL)", nullable = true) TipoRepasse tipoValor,
        @Schema(description = "Percentual", nullable = true) BigDecimal percentual
    ) {
        public AtualizarRepasseRequest(BigDecimal valorRepassar) {
            this(valorRepassar, null, null);
        }
    }
}
