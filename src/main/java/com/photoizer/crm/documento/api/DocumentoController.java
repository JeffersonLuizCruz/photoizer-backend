package com.photoizer.crm.documento.api;

import com.photoizer.crm.agenda.exception.AgendamentoNaoEncontradoException;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.documento.service.ContratoService;
import com.photoizer.crm.shared.storage.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documentos")
@Tag(name = "Documentos", description = "Geração de contratos e recibos")
public class DocumentoController {

    private final ContratoService contratoService;
    private final AgendamentoRepository agendamentoRepository;
    private final FileStorageService fileStorageService;

    public DocumentoController(ContratoService contratoService,
                               AgendamentoRepository agendamentoRepository,
                               FileStorageService fileStorageService) {
        this.contratoService = contratoService;
        this.agendamentoRepository = agendamentoRepository;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/contratos/{agendamentoId}")
    @Operation(summary = "Baixar contrato")
    public ResponseEntity<byte[]> downloadContrato(@PathVariable UUID agendamentoId) {
        var pdf = contratoService.gerarContrato(agendamentoId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=contrato_" + agendamentoId + ".pdf")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf);
    }

    @GetMapping("/recibos/{agendamentoId}")
    @Operation(summary = "Baixar recibo")
    public ResponseEntity<byte[]> downloadRecibo(@PathVariable UUID agendamentoId) {
        var pdf = contratoService.gerarRecibo(agendamentoId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=recibo_" + agendamentoId + ".pdf")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf);
    }

    @GetMapping("/comprovantes/{agendamentoId}/{tipo}")
    @Operation(summary = "Servir comprovante de pagamento (entrada ou final)")
    public ResponseEntity<Resource> downloadComprovante(
            @PathVariable UUID agendamentoId,
            @PathVariable String tipo) {
        var agendamento = agendamentoRepository.findById(agendamentoId)
            .orElseThrow(() -> new AgendamentoNaoEncontradoException(agendamentoId));

        var caminho = switch (tipo) {
            case "entrada" -> agendamento.getUrlComprovanteEntrada();
            case "final" -> agendamento.getUrlComprovanteFinal();
            default -> throw new IllegalArgumentException("Tipo inválido: " + tipo + ". Use 'entrada' ou 'final'.");
        };

        if (caminho == null || caminho.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        var file = new FileSystemResource(Path.of(caminho));
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        var filename = "comprovante_" + tipo + "_" + agendamentoId + ".jpg";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
            .contentType(MediaType.IMAGE_JPEG)
            .body(file);
    }
}
