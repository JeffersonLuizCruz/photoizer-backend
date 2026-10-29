package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.model.EtapaGaleria;
import com.photoizer.crm.ecommerce.service.GaleriaPipelineQueryService;
import com.photoizer.crm.shared.api.PageResponse;
import com.photoizer.crm.shared.config.openapi.OpenApiTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

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
            @RequestParam(defaultValue = "false") boolean semFotos,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int perPage,
            Authentication authentication) {
        return ResponseEntity.ok(pipelineQueryService.listar(
            etapa, busca, atrasadas, page, perPage, incluirFinanceiro(authentication), semFotos));
    }

    @GetMapping("/pipeline/resumo")
    @Operation(summary = "Contadores por etapa para os cards e o badge do menu")
    public ResponseEntity<GaleriaPipelineResumoResponse> resumo(Authentication authentication) {
        return ResponseEntity.ok(pipelineQueryService.resumo(incluirFinanceiro(authentication)));
    }

    @GetMapping("/desempenho")
    @RolesAllowed({"ADMIN", "FOTOGRAFO"})
    @Operation(summary = "Indicadores de SLA, funil e extras da Central de Galerias")
    public ResponseEntity<GaleriaPerformanceResponse> desempenho(
            @RequestParam(defaultValue = "6") int meses) {
        return ResponseEntity.ok(pipelineQueryService.desempenho(meses));
    }

    @GetMapping(value = "/pipeline/export.csv", produces = "text/csv")
    @Operation(summary = "Exportar a fila filtrada em CSV")
    public ResponseEntity<byte[]> exportarCsv(
            @RequestParam(required = false) EtapaGaleria etapa,
            @RequestParam(required = false) String busca,
            @RequestParam(defaultValue = "false") boolean atrasadas,
            @RequestParam(defaultValue = "false") boolean semFotos,
            Authentication authentication) {
        var conteudo = "\uFEFF" + pipelineQueryService.gerarCsv(
            etapa, busca, atrasadas, incluirFinanceiro(authentication), semFotos);
        var nomeArquivo = "galerias-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeArquivo + "\"")
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .body(conteudo.getBytes(StandardCharsets.UTF_8));
    }

    private boolean incluirFinanceiro(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream().anyMatch(autoridade ->
            "ROLE_ADMIN".equals(autoridade.getAuthority())
                || "ROLE_FOTOGRAFO".equals(autoridade.getAuthority()));
    }
}
