package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.service.ComentarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ecommerce/admin/comentarios")
@Tag(name = "Admin Comentários", description = "Comentários dos clientes por foto da galeria")
public class AdminComentariosController {

    private final ComentarioService comentarioService;

    public AdminComentariosController(ComentarioService comentarioService) {
        this.comentarioService = comentarioService;
    }

    @GetMapping("/agendamentos/{agendamentoId}")
    @Operation(summary = "Listar comentários por foto do agendamento (com não lidos)")
    public ResponseEntity<List<ComentariosPorFotoResponse>> listarPorAgendamento(@PathVariable UUID agendamentoId) {
        return ResponseEntity.ok(comentarioService.listarAdmin(agendamentoId));
    }

    @PostMapping("/agendamentos/{agendamentoId}/fotos/{fotoId}/comentarios")
    @Operation(summary = "Responder um comentário de foto (fotógrafo/admin)")
    public ResponseEntity<ComentarioResponse> responder(
            @PathVariable UUID agendamentoId,
            @PathVariable UUID fotoId,
            @Valid @RequestBody ComentarioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(comentarioService.responderStaff(agendamentoId, fotoId, request));
    }

    @PatchMapping("/agendamentos/{agendamentoId}/fotos/{fotoId}/comentarios/lidas")
    @Operation(summary = "Marcar comentários de uma foto como lidos")
    public ResponseEntity<Void> marcarLidos(
            @PathVariable UUID agendamentoId,
            @PathVariable UUID fotoId) {
        comentarioService.marcarLidos(agendamentoId, fotoId);
        return ResponseEntity.ok().build();
    }
}