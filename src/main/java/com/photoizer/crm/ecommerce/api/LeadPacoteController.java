package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.service.LeadPacoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Captação de lead do checkout público de pacotes. Não cria pedido nem cobra:
 * apenas registra o interesse para o comercial entrar em contato.
 */
@RestController
@RequestMapping("/api/v1/ecommerce/leads")
@Tag(name = "Leads de Pacotes", description = "Solicitação de contato feita no catálogo público de pacotes")
public class LeadPacoteController {

    private final LeadPacoteService leadService;

    public LeadPacoteController(LeadPacoteService leadService) {
        this.leadService = leadService;
    }

    @PostMapping
    @Operation(summary = "Registrar solicitação de pacote (lead)")
    public ResponseEntity<LeadPacotePublicoResponse> criar(
            @Valid @RequestBody LeadPacoteRequest request) {
        var lead = leadService.criar(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new LeadPacotePublicoResponse(lead.getId(), lead.getStatus().name()));
    }
}
