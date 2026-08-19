package com.photoizer.crm.agenda.api;

import com.photoizer.crm.agenda.service.RascunhoAgendamentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rascunhos")
@Tag(name = "Rascunhos", description = "Rascunhos de agendamento")
public class RascunhoAgendamentoController {

    private final RascunhoAgendamentoService service;
    private final RascunhoAgendamentoMapper mapper;

    public RascunhoAgendamentoController(RascunhoAgendamentoService service, RascunhoAgendamentoMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    private UUID getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new IllegalStateException("Usuário não autenticado");
        }
        return UUID.fromString(auth.getName());
    }

    @PostMapping
    @Operation(summary = "Salvar ou atualizar rascunho do agendamento")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Rascunho salvo") })
    public ResponseEntity<RascunhoAgendamentoResponse> salvar(
        @RequestParam(required = false) String clienteId,
        @RequestParam(required = false) String nome,
        @RequestParam(required = false) String telefone,
        @RequestParam(required = false) String email,
        @RequestParam(required = false) String cpf,
        @RequestParam(required = false) String cidade,
        @RequestParam(required = false) String estado,
        @RequestParam(required = false) String origem,
        @RequestParam(required = false) String pacoteId,
        @RequestParam(required = false) String data,
        @RequestParam(required = false) String hora,
        @RequestParam(required = false) String localEnsaio,
        @RequestParam(required = false) String enderecoCompleto,
        @RequestParam(required = false) String editorId,
        @RequestParam(required = false) BigDecimal custoDeslocamento,
        @RequestParam(required = false) Boolean repassarDeslocamento,
        @RequestParam(required = false) Boolean autorizaUsoImagem,
        @RequestParam(required = false) String indicadorId,
        @RequestParam(required = false) String indicadorNome,
        @RequestParam(required = false) String indicadorTelefone,
        @RequestParam(required = false) String observacoes,
        @RequestParam(required = false, defaultValue = "0") Integer currentStep,
        @RequestParam(required = false) String comprovanteName,
        @RequestParam(required = false, defaultValue = "false") Boolean confirmado
    ) {
        var usuarioId = getCurrentUserId();
        var draft = service.salvarRascunho(
            usuarioId, clienteId, nome, telefone, email, cpf, cidade, estado,
            origem, pacoteId, data, hora, localEnsaio, enderecoCompleto,
            editorId, custoDeslocamento, repassarDeslocamento, autorizaUsoImagem,
            indicadorId, indicadorNome, indicadorTelefone, observacoes,
            currentStep, comprovanteName, confirmado
        );
        return ResponseEntity.ok(mapper.toResponse(draft));
    }

    @GetMapping("/meu")
    @Operation(summary = "Buscar rascunho do usuário logado")
    public ResponseEntity<RascunhoAgendamentoResponse> buscarMeu() {
        var usuarioId = getCurrentUserId();
        return service.buscarPorUsuario(usuarioId)
            .map(draft -> ResponseEntity.ok(mapper.toResponse(draft)))
            .orElse(ResponseEntity.noContent().build());
    }

    @DeleteMapping("/meu")
    @Operation(summary = "Deletar rascunho do usuário logado")
    @ApiResponses({ @ApiResponse(responseCode = "204", description = "Rascunho deletado") })
    public ResponseEntity<Void> deletarMeu() {
        var usuarioId = getCurrentUserId();
        service.deletarPorUsuario(usuarioId);
        return ResponseEntity.noContent().build();
    }
}
