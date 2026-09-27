package com.photoizer.crm.agenda.api;

import com.photoizer.crm.agenda.service.PropostaPublicaService;
import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "Propostas Públicas", description = "Página pública de assinatura da proposta por token")
public class PropostaPublicaController {

    private final PropostaPublicaService propostaPublicaService;

    public PropostaPublicaController(PropostaPublicaService propostaPublicaService) {
        this.propostaPublicaService = propostaPublicaService;
    }

    @GetMapping("/{token}")
    @Operation(summary = "Carregar proposta pública por token")
    public ResponseEntity<PropostaPublicaResponse> carregar(@PathVariable String token) {
        return ResponseEntity.ok(propostaPublicaService.buscarPublico(token));
    }

    @GetMapping("/{token}/status")
    @Operation(summary = "Consultar status da proposta pelo token")
    public ResponseEntity<PropostaStatusPublicoResponse> status(@PathVariable String token) {
        return ResponseEntity.ok(propostaPublicaService.status(token));
    }

    @PostMapping(value = "/{token}/assinar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Assinar proposta",
        description = "Cliente preenche os próprios dados, escolhe uso de imagem, anexa comprovante e assina (nome + desenho)")
    public ResponseEntity<PropostaStatusPublicoResponse> assinar(
            @PathVariable String token,
            @RequestParam String nome,
            @RequestParam String telefone,
            @RequestParam(required = false) String email,
            @RequestParam String cpf,
            @RequestParam(required = false) String cidade,
            @RequestParam(required = false) String estado,
            @RequestParam String autorizaUsoImagem,
            @RequestParam("assinatura") String assinaturaNome,
            @RequestParam MultipartFile comprovante,
            @RequestParam MultipartFile assinaturaImagem,
            @RequestParam(required = false) String userAgent,
            @RequestParam(required = false) String plataforma,
            @RequestParam(required = false) String fusoHorario,
            HttpServletRequest request) {
        var agendamento = propostaPublicaService.assinar(
            token, nome, telefone, email, cpf, cidade, estado,
            autorizaUsoImagem, assinaturaNome, comprovante, assinaturaImagem,
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
