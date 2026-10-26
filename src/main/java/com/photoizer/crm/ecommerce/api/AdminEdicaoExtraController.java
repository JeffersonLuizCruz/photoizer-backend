package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.service.CompraQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Fila de edição sob demanda das fotos extras compradas depois da publicação.
 * Acessível também ao EDITOR, que é quem opera o workspace de edição.
 */
@RestController
@RequestMapping("/api/v1/admin/ecommerce/extras")
@RolesAllowed({"ADMIN", "FOTOGRAFO", "EDITOR"})
@Tag(name = "Admin Edição de Extras",
    description = "Extras comprados após a publicação e aguardando edição sob demanda")
public class AdminEdicaoExtraController {

    private final CompraQueryService compraQueryService;

    public AdminEdicaoExtraController(CompraQueryService compraQueryService) {
        this.compraQueryService = compraQueryService;
    }

    @GetMapping("/pendentes-edicao")
    @Operation(summary = "Listar compras com fotos extras aguardando edição")
    public ResponseEntity<List<CompraExtraPendenteEdicaoResponse>> pendentes(
            @RequestParam(required = false) UUID agendamentoId) {
        return ResponseEntity.ok(compraQueryService.listarPendentesEdicao(agendamentoId));
    }
}
