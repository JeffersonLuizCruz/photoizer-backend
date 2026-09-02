package com.photoizer.crm.fotografo.api;

import com.photoizer.crm.auth.api.UserResponse;
import com.photoizer.crm.despesa.api.DespesaMapper;
import com.photoizer.crm.fotografo.service.FotografoCsvExporter;
import com.photoizer.crm.fotografo.service.FotografoQueryService;
import com.photoizer.crm.fotografo.service.FotografoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fotografos")
@Tag(name = "Fotógrafos", description = "Módulo de gestão financeira de fotógrafos")
public class FotografoController {

    private final FotografoService fotografoService;
    private final FotografoQueryService fotografoQueryService;
    private final FotografoCsvExporter csvExporter;
    private final DespesaMapper despesaMapper;

    public FotografoController(FotografoService fotografoService,
                               FotografoQueryService fotografoQueryService,
                               FotografoCsvExporter csvExporter,
                               DespesaMapper despesaMapper) {
        this.fotografoService = fotografoService;
        this.fotografoQueryService = fotografoQueryService;
        this.csvExporter = csvExporter;
        this.despesaMapper = despesaMapper;
    }

    @GetMapping
    @Operation(summary = "Listar todos os fotógrafos")
    public ResponseEntity<List<UserResponse>> listar() {
        return ResponseEntity.ok(fotografoService.listarFotografos());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar fotógrafo por ID")
    public ResponseEntity<UserResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(fotografoService.buscarPorId(id));
    }

    @PostMapping
    @Operation(summary = "Criar novo fotógrafo")
    @RolesAllowed("ADMIN")
    public ResponseEntity<UserResponse> criar(@Valid @RequestBody CriarFotografoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fotografoService.criar(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar dados do fotógrafo")
    @RolesAllowed("ADMIN")
    public ResponseEntity<UserResponse> atualizar(@PathVariable UUID id,
                                           @Valid @RequestBody AtualizarFotografoRequest request) {
        return ResponseEntity.ok(fotografoService.atualizar(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Ativar/desativar fotógrafo")
    @RolesAllowed("ADMIN")
    public ResponseEntity<Void> toggleStatus(@PathVariable UUID id) {
        fotografoService.toggleStatus(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remover fotógrafo (apenas se sem ensaios)")
    @RolesAllowed("ADMIN")
    public ResponseEntity<Void> remover(@PathVariable UUID id) {
        fotografoService.remover(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/dashboard")
    @Operation(summary = "Dashboard do fotógrafo com resumo financeiro")
    public ResponseEntity<FotografoDashboardResponse> dashboard(@PathVariable UUID id) {
        return ResponseEntity.ok(fotografoQueryService.dashboard(id));
    }

    @GetMapping("/{id}/ensaios")
    @Operation(summary = "Listar ensaios do fotógrafo")
    public ResponseEntity<List<FotografoEnsaiosResponse>> listarEnsaios(@PathVariable UUID id) {
        return ResponseEntity.ok(fotografoQueryService.listarEnsaios(id));
    }

    @GetMapping("/{id}/resumo-financeiro")
    @Operation(summary = "Resumo financeiro detalhado do fotógrafo")
    public ResponseEntity<FotografoResumoFinanceiroResponse> resumoFinanceiro(@PathVariable UUID id) {
        return ResponseEntity.ok(fotografoQueryService.resumoFinanceiro(id));
    }

    @GetMapping("/{id}/custos")
    @Operation(summary = "Listar custos/despesas do fotógrafo")
    public ResponseEntity<List<com.photoizer.crm.despesa.api.DespesaResponse>> listarCustos(@PathVariable UUID id) {
        var custos = fotografoService.listarCustos(id).stream()
            .map(despesaMapper::toResponse)
            .toList();
        return ResponseEntity.ok(custos);
    }

    @GetMapping("/relatorio-global")
    @Operation(summary = "Relatório consolidado de todos os fotógrafos")
    public ResponseEntity<FotografoRelatorioGlobalResponse> relatorioGlobal() {
        return ResponseEntity.ok(fotografoQueryService.relatorioGlobal());
    }

    @GetMapping("/{id}/financeiro/csv")
    @Operation(summary = "Exportar finanças do fotógrafo em CSV")
    public ResponseEntity<byte[]> exportarCsv(@PathVariable UUID id) {
        var fotografo = fotografoService.buscarPorId(id);

        var ensaios = fotografoQueryService.listarEnsaios(id);
        var csv = csvExporter.exportar(ensaios, fotografo.nome());

        var filename = "financas-" + fotografo.nome().replaceAll("\\s+", "-").toLowerCase() + ".csv";

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(csv);
    }
}
