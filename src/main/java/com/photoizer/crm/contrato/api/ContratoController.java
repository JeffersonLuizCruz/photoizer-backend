package com.photoizer.crm.contrato.api;

import com.photoizer.crm.contrato.model.StatusContrato;
import com.photoizer.crm.contrato.service.GestaoContratoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contratos")
@Tag(name = "Contratos", description = "Gestão de contratos de prestação de serviços e aprovação")
public class ContratoController {

    private final GestaoContratoService contratoService;

    public ContratoController(GestaoContratoService contratoService) {
        this.contratoService = contratoService;
    }

    @GetMapping
    @Operation(summary = "Listar contratos", description = "Filtra por status e busca por cliente/pacote")
    public ResponseEntity<?> listar(
            @RequestParam(required = false) StatusContrato status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var result = contratoService.listar(status, search, page, size);
        var contratos = result.getContent().stream()
            .map(ContratoResponse::of)
            .toList();
        return ResponseEntity.ok(new java.util.HashMap<String, Object>() {{
            put("data", contratos);
            put("total", result.getTotalElements());
            put("page", result.getNumber());
            put("size", result.getSize());
            put("totalPages", result.getTotalPages());
        }});
    }

    @PostMapping
    @Operation(summary = "Criar contrato", description = "Cria um contrato em rascunho com os dados do sistema")
    public ResponseEntity<ContratoResponse> criar(@Valid @RequestBody CriarContratoRequest request) {
        var contrato = contratoService.criar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ContratoResponse.of(contrato));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar contrato por ID")
    public ResponseEntity<ContratoResponse> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(ContratoResponse.of(contratoService.buscar(id)));
    }

    @PostMapping("/{id}/publicar")
    @Operation(summary = "Publicar contrato", description = "Gera o token e o link público para o cliente")
    public ResponseEntity<PublicarContratoResponse> publicar(@PathVariable UUID id) {
        return ResponseEntity.ok(contratoService.publicar(id));
    }

    @PostMapping("/{id}/confirmar-pagamento")
    @Operation(summary = "Confirmar pagamento", description = "Admin confere o comprovante de pagamento da reserva")
    public ResponseEntity<ContratoResponse> confirmarPagamento(@PathVariable UUID id) {
        return ResponseEntity.ok(ContratoResponse.of(contratoService.confirmarPagamento(id)));
    }

    @PostMapping("/{id}/aprovar")
    @Operation(summary = "Aprovar contrato", description = "Aprova o contrato e dispara a criação do agendamento na agenda")
    public ResponseEntity<ContratoResponse> aprovar(@PathVariable UUID id) {
        return ResponseEntity.ok(ContratoResponse.of(contratoService.aprovar(id)));
    }

    @PostMapping("/{id}/devolver")
    @Operation(summary = "Devolver contrato", description = "Devolve ao cliente com o motivo (comprovante inválido ou correção de termos)")
    public ResponseEntity<ContratoResponse> devolver(
            @PathVariable UUID id,
            @Valid @RequestBody DevolverContratoRequest request) {
        return ResponseEntity.ok(ContratoResponse.of(contratoService.devolver(id, request)));
    }

    @PostMapping("/{id}/cancelar")
    @Operation(summary = "Cancelar contrato")
    public ResponseEntity<ContratoResponse> cancelar(@PathVariable UUID id) {
        return ResponseEntity.ok(ContratoResponse.of(contratoService.cancelar(id)));
    }

    @GetMapping("/{id}/pdf")
    @Operation(summary = "Baixar PDF do contrato assinado (snapshot imutável)")
    public ResponseEntity<Resource> downloadPdf(@PathVariable UUID id) {
        var contrato = contratoService.buscar(id);
        if (contrato.getUrlPdf() == null) {
            return ResponseEntity.notFound().build();
        }
        return serveArquivo(contrato.getUrlPdf(), "contrato_" + id + ".pdf", MediaType.APPLICATION_PDF);
    }

    @GetMapping("/{id}/comprovante")
    @Operation(summary = "Exibir comprovante de pagamento da reserva")
    public ResponseEntity<Resource> downloadComprovante(@PathVariable UUID id) {
        var contrato = contratoService.buscar(id);
        if (contrato.getUrlComprovanteEntrada() == null) {
            return ResponseEntity.notFound().build();
        }
        var tipo = contentType(contrato.getUrlComprovanteEntrada());
        return serveArquivo(contrato.getUrlComprovanteEntrada(), "comprovante",
            tipo != null ? tipo : MediaType.APPLICATION_OCTET_STREAM);
    }

    /**
     * Pattern: Defense in Depth — valida que o caminho esta dentro do diretorio
     * de uploads antes de servir o arquivo, prevenindo path traversal se o banco
     * for adulterado com um caminho como "../../etc/passwd".
     */
    private ResponseEntity<Resource> serveArquivo(String caminho, String filename, MediaType tipo) {
        var uploadDir = Path.of("uploads").toAbsolutePath().normalize();
        var file = Path.of(caminho).toAbsolutePath().normalize();
        if (!file.startsWith(uploadDir)) {
            return ResponseEntity.badRequest().build();
        }
        var resource = new FileSystemResource(file);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
            .contentType(tipo)
            .body(resource);
    }

    private MediaType contentType(String caminho) {
        try {
            var tipo = Files.probeContentType(Path.of(caminho));
            return tipo != null ? MediaType.parseMediaType(tipo) : null;
        } catch (Exception e) {
            return null;
        }
    }
}