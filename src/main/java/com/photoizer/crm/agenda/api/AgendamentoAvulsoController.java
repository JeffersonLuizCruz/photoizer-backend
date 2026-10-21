package com.photoizer.crm.agenda.api;

import com.photoizer.crm.agenda.service.AgendamentoAvulsoService;
import com.photoizer.crm.agenda.service.CriarAgendamentoAvulsoCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agendamentos/avulso")
@RolesAllowed({"ADMIN", "AGENDADOR"})
@Tag(name = "Agendamentos", description = "Gestão de agendamentos de ensaios fotográficos")
public class AgendamentoAvulsoController {

    private final AgendamentoAvulsoService agendamentoAvulsoService;

    public AgendamentoAvulsoController(AgendamentoAvulsoService agendamentoAvulsoService) {
        this.agendamentoAvulsoService = agendamentoAvulsoService;
    }

    @GetMapping("/opcoes")
    @Operation(operationId = "opcoesAgendamentoAvulso", summary = "Opções do agendamento avulso",
        description = "Lista fotógrafos, editores e valores padrão sem expor a configuração completa.")
    public ResponseEntity<OpcoesAgendamentoAvulsoResponse> opcoes() {
        return ResponseEntity.ok(agendamentoAvulsoService.opcoes());
    }

    @PostMapping
    @Operation(operationId = "criarAgendamentoAvulso",
        summary = "Criar agendamento avulso",
        description = "Cria um ensaio já confirmado pelo staff, com cliente e valores informados manualmente.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Agendamento criado"),
        @ApiResponse(responseCode = "409", description = "Conflito de agenda"),
        @ApiResponse(responseCode = "422", description = "Dados inválidos")
    })
    public ResponseEntity<AgendamentoResponse> criar(
            @Valid @RequestBody CriarAgendamentoAvulsoRequest request) {
        var command = new CriarAgendamentoAvulsoCommand(
            request.clienteNome(),
            request.clienteTelefone(),
            request.clienteEmail(),
            request.clienteCpf(),
            request.clienteCidade(),
            request.clienteEstado(),
            request.pacoteId(),
            request.fotografoId(),
            request.dataHoraEnsaio(),
            request.duracaoMinutos(),
            request.localEnsaio(),
            request.valorTotal(),
            request.valorEntradaExigido(),
            request.valorPago(),
            request.dataPagamento(),
            request.formaPagamento(),
            request.tipoPagamento(),
            request.custoDeslocamento(),
            request.repassarDeslocamento(),
            request.autorizaUsoImagem(),
            request.observacoes()
        );

        var agendamento = agendamentoAvulsoService.criar(command);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(respostaPara(agendamentoAvulsoService.toResponse(agendamento)));
    }

    private AgendamentoResponse respostaPara(AgendamentoResponse response) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return response;
        }
        var semMetricas = authentication.getAuthorities().stream()
            .map(org.springframework.security.core.GrantedAuthority::getAuthority)
            .anyMatch("ROLE_AGENDADOR"::equals);
        return semMetricas ? response.ocultarDadosInternos() : response;
    }
}
