package com.photoizer.crm.bloqueio.api;

import com.photoizer.crm.bloqueio.service.BloqueioAgendaCommand;
import com.photoizer.crm.bloqueio.service.BloqueioAgendaService;
import com.photoizer.crm.shared.exception.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bloqueios")
@Tag(name = "Bloqueios de Agenda", description = "Compromissos que impedem novos agendamentos")
@RolesAllowed({"ADMIN", "FOTOGRAFO", "EDITOR", "AGENDADOR"})
public class BloqueioAgendaController {

    private final BloqueioAgendaService bloqueioAgendaService;
    private final BloqueioAgendaMapper bloqueioAgendaMapper;

    public BloqueioAgendaController(BloqueioAgendaService bloqueioAgendaService,
                                    BloqueioAgendaMapper bloqueioAgendaMapper) {
        this.bloqueioAgendaService = bloqueioAgendaService;
        this.bloqueioAgendaMapper = bloqueioAgendaMapper;
    }

    @GetMapping
    @Operation(operationId = "listarBloqueiosAgenda", summary = "Listar compromissos no período")
    public ResponseEntity<List<BloqueioAgendaResponse>> listar(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @Parameter(description = "Início do período (ISO-8601)") LocalDateTime dataInicio,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @Parameter(description = "Fim do período, exclusivo (ISO-8601)") LocalDateTime dataFim,
            @RequestParam(required = false)
            @Parameter(description = "Filtrar por profissional (inclui bloqueios globais)") UUID profissionalId) {
        var agora = LocalDateTime.now();
        var inicio = dataInicio != null ? dataInicio : agora.minusMonths(1);
        var fim = dataFim != null ? dataFim : agora.plusMonths(3);

        var bloqueios = bloqueioAgendaService.listar(inicio, fim, profissionalId)
            .stream().map(bloqueioAgendaMapper::toResponse).toList();
        return ResponseEntity.ok(bloqueios);
    }

    @GetMapping("/{id}")
    @Operation(operationId = "buscarBloqueioAgenda", summary = "Buscar compromisso por ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Compromisso encontrado"),
        @ApiResponse(responseCode = "404", description = "Compromisso não encontrado")
    })
    public ResponseEntity<BloqueioAgendaResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(bloqueioAgendaMapper.toResponse(bloqueioAgendaService.buscarPorId(id)));
    }

    @PostMapping
    @Operation(operationId = "criarBloqueioAgenda", summary = "Criar compromisso na agenda")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Compromisso criado"),
        @ApiResponse(responseCode = "409", description = "Conflito com compromisso ou ensaio existente"),
        @ApiResponse(responseCode = "422", description = "Período ou profissional inválido")
    })
    public ResponseEntity<BloqueioAgendaResponse> criar(
            @Valid @RequestBody BloqueioAgendaRequest request,
            @AuthenticationPrincipal String userId) {
        var bloqueio = bloqueioAgendaService.criar(toCommand(request), requireUserId(userId));
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(bloqueioAgendaMapper.toResponse(bloqueio));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "atualizarBloqueioAgenda", summary = "Atualizar compromisso")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Compromisso atualizado"),
        @ApiResponse(responseCode = "403", description = "Sem permissão para alterar"),
        @ApiResponse(responseCode = "404", description = "Compromisso não encontrado"),
        @ApiResponse(responseCode = "409", description = "Conflito com compromisso ou ensaio existente")
    })
    public ResponseEntity<BloqueioAgendaResponse> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody BloqueioAgendaRequest request,
            @AuthenticationPrincipal String userId) {
        var bloqueio = bloqueioAgendaService.atualizar(
            id, toCommand(request), requireUserId(userId), isAdmin());
        return ResponseEntity.ok(bloqueioAgendaMapper.toResponse(bloqueio));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "cancelarBloqueioAgenda", summary = "Cancelar compromisso")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Compromisso cancelado"),
        @ApiResponse(responseCode = "403", description = "Sem permissão para cancelar"),
        @ApiResponse(responseCode = "404", description = "Compromisso não encontrado")
    })
    public ResponseEntity<Void> cancelar(
            @PathVariable UUID id,
            @AuthenticationPrincipal String userId) {
        bloqueioAgendaService.cancelar(id, requireUserId(userId), isAdmin());
        return ResponseEntity.noContent().build();
    }

    private BloqueioAgendaCommand toCommand(BloqueioAgendaRequest request) {
        return new BloqueioAgendaCommand(
            request.titulo(), request.descricao(), request.inicio(), request.fim(), request.profissionalId());
    }

    private UUID requireUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new UnauthorizedException("Usuário autenticado não identificado.");
        }
        return UUID.fromString(userId);
    }

    private boolean isAdmin() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
            .map(org.springframework.security.core.GrantedAuthority::getAuthority)
            .anyMatch("ROLE_ADMIN"::equals);
    }
}
