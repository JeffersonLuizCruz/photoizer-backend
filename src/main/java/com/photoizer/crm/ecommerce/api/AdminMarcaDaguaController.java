package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.service.GaleriaCapaService;
import com.photoizer.crm.foto.service.FotoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Manutenção global da marca d'água das fotos já enviadas.
 *
 * <p>O texto é gravado no arquivo no momento do upload. Quando o nome da
 * empresa é configurado depois, as fotos antigas continuam com a marca
 * anterior até serem reprocessadas por esta rotina.
 */
@RestController
@RequestMapping("/api/v1/admin/galerias/marca-dagua")
@RolesAllowed("ADMIN")
@Tag(name = "Admin Galeria", description = "Modo, fase e manutenção da galeria")
public class AdminMarcaDaguaController {

    private final FotoService fotoService;
    private final GaleriaCapaService capaService;

    public AdminMarcaDaguaController(FotoService fotoService,
                                     GaleriaCapaService capaService) {
        this.fotoService = fotoService;
        this.capaService = capaService;
    }

    @GetMapping("/fotos")
    @Operation(summary = "Listar fotos para regeneração da marca d'água")
    public ResponseEntity<List<MarcaDaguaFotoResponse>> listarFotos() {
        return ResponseEntity.ok(fotoService.listarTodas().stream()
            .map(MarcaDaguaFotoResponse::of)
            .toList());
    }

    @PostMapping("/fotos/{fotoId}/regerar")
    @Operation(summary = "Regenerar a marca d'água de uma foto com o nome atual")
    public ResponseEntity<Void> regerar(@PathVariable UUID fotoId) {
        var foto = fotoService.regerarMarcaDagua(fotoId);
        capaService.removerHeroes(foto.getAgendamentoId(), List.of(fotoId));
        return ResponseEntity.noContent().build();
    }
}
