package com.photoizer.crm.agenda.api;

import com.photoizer.crm.agenda.service.AgendamentoService;
import com.photoizer.crm.shared.config.openapi.OpenApiTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Ensaios do cliente autenticado (area "Meus Ensaios").
 *
 * <p>Mora no modulo agenda porque a consulta e o DTO pertencem a esse agregado;
 * a autenticacao e feita pelo JWT de cliente (papel CLIENTE).
 */
@RestController
@RequestMapping("/api/v1/auth/cliente/agendamentos")
@RolesAllowed("CLIENTE")
@Tag(name = OpenApiTags.AGENDAMENTOS, description = "Ensaios do cliente autenticado")
public class ClienteAgendamentoController {

    private final AgendamentoService agendamentoService;

    public ClienteAgendamentoController(AgendamentoService agendamentoService) {
        this.agendamentoService = agendamentoService;
    }

    @GetMapping
    @Operation(summary = "Listar os ensaios do cliente autenticado")
    public ResponseEntity<List<AgendamentoClienteResponse>> listar(
            @AuthenticationPrincipal String clienteId) {
        return ResponseEntity.ok(
            agendamentoService.listarAgendamentosCliente(UUID.fromString(clienteId)));
    }
}
