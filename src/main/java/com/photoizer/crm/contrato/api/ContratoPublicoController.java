package com.photoizer.crm.contrato.api;

import com.photoizer.crm.contrato.service.ContratoPublicoService;
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
@RequestMapping("/api/v1/contratos/publico")
@Tag(name = "Contratos Públicos", description = "Página pública do contrato por token")
public class ContratoPublicoController {

    private final ContratoPublicoService contratoPublicoService;

    public ContratoPublicoController(ContratoPublicoService contratoPublicoService) {
        this.contratoPublicoService = contratoPublicoService;
    }

    @GetMapping("/{token}")
    @Operation(summary = "Carregar contrato público por token")
    public ResponseEntity<ContratoPublicoResponse> carregar(@PathVariable String token) {
        return ResponseEntity.ok(contratoPublicoService.buscarPublico(token));
    }

    @GetMapping("/{token}/status")
    @Operation(summary = "Consultar status do contrato pelo token")
    public ResponseEntity<ContratoStatusPublicoResponse> status(@PathVariable String token) {
        return ResponseEntity.ok(contratoPublicoService.status(token));
    }

    @PostMapping(value = "/{token}/assinar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Assinar contrato", description = "Preenche dados do cliente, anexa comprovante da reserva e assina digitalmente")
    public ResponseEntity<ContratoStatusPublicoResponse> assinar(
            @PathVariable String token,
            @RequestParam String nome,
            @RequestParam String telefone,
            @RequestParam(required = false) String email,
            @RequestParam String cpf,
            @RequestParam(required = false) String cidade,
            @RequestParam(required = false) String estado,
            @RequestParam String autorizaUsoImagem,
            @RequestParam("assinatura") String nomeAssina,
            @RequestParam MultipartFile comprovante,
            HttpServletRequest request) {
        var contrato = contratoPublicoService.assinar(
            token, nome, telefone, email, cpf, cidade, estado,
            autorizaUsoImagem, nomeAssina, comprovante, obterIp(request));
        return ResponseEntity.ok(ContratoStatusPublicoResponse.of(contrato));
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