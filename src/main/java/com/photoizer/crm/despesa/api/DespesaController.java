package com.photoizer.crm.despesa.api;

import com.photoizer.crm.despesa.model.StatusDespesa;
import com.photoizer.crm.despesa.service.DespesaCategoriaService;
import com.photoizer.crm.despesa.service.DespesaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/despesas")
@Tag(name = "Despesas", description = "Despesas, categorias e comprovantes")
@RolesAllowed({"ADMIN", "FOTOGRAFO", "EDITOR"})
public class DespesaController {

    private final DespesaService despesaService;
    private final DespesaCategoriaService categoriaService;
    private final DespesaMapper despesaMapper;

    public DespesaController(DespesaService despesaService,
                             DespesaCategoriaService categoriaService,
                             DespesaMapper despesaMapper) {
        this.despesaService = despesaService;
        this.categoriaService = categoriaService;
        this.despesaMapper = despesaMapper;
    }

    @GetMapping
    @Operation(summary = "Listar despesas com filtros e ordenação")
    public ResponseEntity<List<DespesaResponse>> listar(
            @RequestParam(required = false) LocalDate dataInicio,
            @RequestParam(required = false) LocalDate dataFim,
            @RequestParam(required = false) UUID categoriaId,
            @RequestParam(required = false) StatusDespesa status,
            @RequestParam(required = false) UUID agendamentoId,
            @RequestParam(required = false) UUID fotografoId,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        var despesas = despesaService
            .listar(dataInicio, dataFim, categoriaId, status, agendamentoId, fotografoId, sortBy, sortDir)
            .stream().map(despesaMapper::toResponse).toList();
        return ResponseEntity.ok(despesas);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar despesa por ID")
    public ResponseEntity<DespesaResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(despesaMapper.toResponse(despesaService.buscarPorId(id)));
    }

    @PostMapping
    @Operation(summary = "Criar nova despesa")
    public ResponseEntity<DespesaResponse> criar(@Valid @RequestBody DespesaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(despesaMapper.toResponse(despesaService.criar(request)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar despesa")
    public ResponseEntity<DespesaResponse> atualizar(@PathVariable UUID id,
                                                     @Valid @RequestBody DespesaRequest request) {
        return ResponseEntity.ok(despesaMapper.toResponse(despesaService.atualizar(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remover despesa")
    public ResponseEntity<Void> remover(@PathVariable UUID id) {
        despesaService.remover(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/pagar")
    @Operation(summary = "Marcar despesa como paga")
    public ResponseEntity<DespesaResponse> marcarComoPaga(@PathVariable UUID id) {
        return ResponseEntity.ok(despesaMapper.toResponse(despesaService.marcarComoPaga(id)));
    }

    @PatchMapping("/{id}/agendamento")
    @Operation(summary = "Vincular ou desvincular despesa de um trabalho (agendamento)")
    public ResponseEntity<DespesaResponse> vincularAgendamento(@PathVariable UUID id,
                                                               @RequestBody DespesaAgendamentoRequest request) {
        return ResponseEntity.ok(despesaMapper.toResponse(despesaService.vincularAgendamento(id, request.agendamentoId())));
    }

    @PatchMapping("/{id}/fotografo")
    @Operation(summary = "Vincular ou desvincular despesa de um fotógrafo")
    public ResponseEntity<DespesaResponse> vincularFotografo(@PathVariable UUID id,
                                                             @RequestBody DespesaFotografoRequest request) {
        return ResponseEntity.ok(despesaMapper.toResponse(despesaService.vincularFotografo(id, request.fotografoId())));
    }

    @PostMapping("/{id}/comprovante")
    @Operation(summary = "Anexar comprovante de despesa")
    public ResponseEntity<DespesaResponse> anexarComprovante(
            @PathVariable UUID id,
            @RequestPart("arquivo") MultipartFile arquivo) {
        return ResponseEntity.ok(despesaMapper.toResponse(despesaService.anexarComprovante(id, arquivo)));
    }

    @GetMapping("/recorrentes-proximas")
    @Operation(summary = "Despesas recorrentes com vencimento nos próximos dias")
    public ResponseEntity<List<DespesaResponse>> recorrentesProximas(
            @RequestParam(defaultValue = "7") int dias) {
        var despesas = despesaService.recorrentesProximas(dias).stream()
            .map(despesaMapper::toResponse).toList();
        return ResponseEntity.ok(despesas);
    }

    // ---- Categorias (delegadas para DespesaCategoriaService) ----

    @GetMapping("/categorias")
    @Operation(summary = "Listar categorias de despesa")
    public ResponseEntity<List<DespesaCategoriaResponse>> listarCategorias(
            @RequestParam(required = false, defaultValue = "true") Boolean ativas) {
        var categorias = categoriaService.listar(ativas).stream()
            .map(c -> despesaMapper.toCategoriaResponse(c, categoriaService.contarDespesas(c.getId())))
            .toList();
        return ResponseEntity.ok(categorias);
    }

    @PostMapping("/categorias")
    @Operation(summary = "Criar categoria de despesa")
    public ResponseEntity<DespesaCategoriaResponse> criarCategoria(@Valid @RequestBody DespesaCategoriaRequest request) {
        var categoria = categoriaService.criar(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(despesaMapper.toCategoriaResponse(categoria, 0));
    }

    @PutMapping("/categorias/{id}")
    @Operation(summary = "Atualizar categoria de despesa")
    public ResponseEntity<DespesaCategoriaResponse> atualizarCategoria(@PathVariable UUID id,
                                                                       @Valid @RequestBody DespesaCategoriaRequest request) {
        var categoria = categoriaService.atualizar(id, request);
        return ResponseEntity.ok(despesaMapper.toCategoriaResponse(categoria, 0));
    }

    @DeleteMapping("/categorias/{id}")
    @Operation(summary = "Remover ou inativar categoria de despesa")
    public ResponseEntity<Void> removerCategoria(@PathVariable UUID id) {
        categoriaService.remover(id);
        return ResponseEntity.noContent().build();
    }
}
