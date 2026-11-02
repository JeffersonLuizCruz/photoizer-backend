package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.service.MensagemGaleriaService;
import com.photoizer.crm.shared.config.openapi.OpenApiTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/galerias")
@RolesAllowed({"ADMIN", "FOTOGRAFO", "EDITOR"})
@Tag(name = OpenApiTags.ADMIN_GALERIAS, description = "Fila operacional do ciclo de entrega da galeria")
public class AdminGaleriaMensagemController {

    private final MensagemGaleriaService mensagemGaleriaService;

    public AdminGaleriaMensagemController(MensagemGaleriaService mensagemGaleriaService) {
        this.mensagemGaleriaService = mensagemGaleriaService;
    }

    @GetMapping("/mensagem-link")
    @Operation(summary = "Obter o modelo de mensagem para envio do link da galeria ao cliente")
    public ResponseEntity<MensagemLinkGaleriaResponse> mensagemLink() {
        return ResponseEntity.ok(new MensagemLinkGaleriaResponse(mensagemGaleriaService.mensagem()));
    }
}
