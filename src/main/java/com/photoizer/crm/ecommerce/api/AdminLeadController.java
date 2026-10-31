package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.model.StatusLead;
import com.photoizer.crm.ecommerce.service.LeadPacoteService;
import com.photoizer.crm.shared.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/leads")
@RolesAllowed({"ADMIN", "FOTOGRAFO", "AGENDADOR"})
@Tag(name = "Admin Leads", description = "Gestão dos leads captados pelo checkout público de pacotes")
public class AdminLeadController {

    private final LeadPacoteService leadService;

    public AdminLeadController(LeadPacoteService leadService) {
        this.leadService = leadService;
    }

    @GetMapping
    @Operation(summary = "Listar leads de pacotes")
    public ResponseEntity<PageResponse<LeadPacoteResponse>> listar(
            @RequestParam(required = false) StatusLead status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int perPage) {
        var pageResult = leadService.listar(status, page, perPage);
        return ResponseEntity.ok(
            PageResponse.from(pageResult.map(LeadPacoteResponse::of), Math.max(page, 1)));
    }

    @GetMapping("/novos")
    @Operation(summary = "Contar leads novos (badge do menu)")
    public ResponseEntity<Long> contarNovos() {
        return ResponseEntity.ok(leadService.contarNovos());
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Atualizar o status comercial do lead")
    public ResponseEntity<LeadPacoteResponse> atualizarStatus(
            @PathVariable UUID id,
            @Valid @RequestBody AtualizarStatusLeadRequest request) {
        return ResponseEntity.ok(leadService.atualizarStatus(id, request.status()));
    }
}
