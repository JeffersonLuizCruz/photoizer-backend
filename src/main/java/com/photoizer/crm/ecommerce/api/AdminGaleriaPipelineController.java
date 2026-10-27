package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.model.EtapaGaleria;
import com.photoizer.crm.ecommerce.service.GaleriaPipelineQueryService;
import com.photoizer.crm.shared.api.PageResponse;
import com.photoizer.crm.shared.config.openapi.OpenApiTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/galerias")
@RolesAllowed({"ADMIN", "FOTOGRAFO", "EDITOR"})
@Tag(name = OpenApiTags.ADMIN_GALERIAS, description = "Fila operacional do ciclo de entrega da galeria")
public class AdminGaleriaPipelineController {

    private final GaleriaPipelineQueryService pipelineQueryService;

    public AdminGaleriaPipelineController(GaleriaPipelineQueryService pipelineQueryService) {
        this.pipelineQueryService = pipelineQueryService;
    }

    @GetMapping("/pipeline")
    @Operation(summary = "Listar a fila de galerias com a etapa operacional de cada ensaio")
    public ResponseEntity<PageResponse<GaleriaPipelineItemResponse>> pipeline(
            @RequestParam(required = false) EtapaGaleria etapa,
            @RequestParam(required = false) String busca,
            @RequestParam(defaultValue = "false") boolean atrasadas,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int perPage) {
        return ResponseEntity.ok(pipelineQueryService.listar(etapa, busca, atrasadas, page, perPage));
    }

    @GetMapping("/pipeline/resumo")
    @Operation(summary = "Contadores por etapa para os cards e o badge do menu")
    public ResponseEntity<GaleriaPipelineResumoResponse> resumo() {
        return ResponseEntity.ok(pipelineQueryService.resumo());
    }
}
