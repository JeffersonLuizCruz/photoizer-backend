package com.photoizer.crm.agenda.api;

import com.photoizer.crm.agenda.service.PropostaPublicaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/propostas/publico")
@Tag(name = "Propostas Públicas", description = "Página pública de assinatura da proposta por token.")
public class PropostaPublicaController {

    private final PropostaPublicaService propostaPublicaService;

    public PropostaPublicaController(PropostaPublicaService propostaPublicaService) {
        this.propostaPublicaService = propostaPublicaService;
    }

    @GetMapping("/{token}")
    @Operation(operationId = "carregarPropostaPublica", summary = "Carregar proposta pública por token")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Proposta encontrada",
            content = @Content(schema = @Schema(implementation = PropostaPublicaResponse.class))),
        @ApiResponse(responseCode = "404", description = "Token inválido")
    })
    public ResponseEntity<PropostaPublicaResponse> carregar(
            @PathVariable @Parameter(description = "Token público da proposta") String token) {
        return ResponseEntity.ok(propostaPublicaService.buscarPublico(token));
    }

    @GetMapping("/{token}/status")
    @Operation(operationId = "statusPropostaPublica", summary = "Consultar status da proposta pelo token")
    public ResponseEntity<PropostaStatusPublicoResponse> status(
            @PathVariable @Parameter(description = "Token público da proposta") String token) {
        return ResponseEntity.ok(propostaPublicaService.status(token));
    }

    @PostMapping(value = "/{token}/assinar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(operationId = "assinarProposta", summary = "Assinar proposta",
        description = "Cliente preenche os próprios dados, escolhe uso de imagem, anexa comprovante e confirma a proposta")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Proposta assinada"),
        @ApiResponse(responseCode = "404", description = "Token inválido"),
        @ApiResponse(responseCode = "422", description = "Dados ou arquivos inválidos")
    })
    public ResponseEntity<PropostaStatusPublicoResponse> assinar(
            @PathVariable @Parameter(description = "Token público da proposta") String token,
            @Parameter(description = "Nome do cliente") @RequestParam String nome,
            @Parameter(description = "Telefone do cliente") @RequestParam String telefone,
            @Parameter(description = "E-mail do cliente") @RequestParam(required = false) String email,
            @Parameter(description = "CPF do cliente") @RequestParam String cpf,
            @Parameter(description = "Cidade") @RequestParam(required = false) String cidade,
            @Parameter(description = "UF") @RequestParam(required = false) String estado,
            @Parameter(description = "Autoriza uso de imagem (true/false)") @RequestParam String autorizaUsoImagem,
            @Parameter(description = "Comprovante de pagamento (imagem/PDF); opcional no agendamento avulso")
            @RequestParam(required = false) MultipartFile comprovante,
            @Parameter(description = "User-Agent do navegador") @RequestParam(required = false) String userAgent,
            @Parameter(description = "Plataforma") @RequestParam(required = false) String plataforma,
            @Parameter(description = "Fuso horário") @RequestParam(required = false) String fusoHorario,
            HttpServletRequest request) {
        var agendamento = propostaPublicaService.assinar(
            token, nome, telefone, email, cpf, cidade, estado,
            autorizaUsoImagem, comprovante,
            userAgent, plataforma, fusoHorario, obterIp(request));
        return ResponseEntity.ok(PropostaStatusPublicoResponse.of(agendamento));
    }

    private String obterIp(HttpServletRequest request) {
        var ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        } else {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
