package com.photoizer.crm.documento.api;

import com.photoizer.crm.agenda.exception.AgendamentoNaoEncontradoException;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.documento.model.TipoComprovante;
import com.photoizer.crm.documento.service.DocumentoService;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documentos")
@Tag(name = "Documentos", description = "Geração de contratos e recibos")
public class DocumentoController {

    private final DocumentoService documentoService;
    private final AgendamentoRepository agendamentoRepository;

    public DocumentoController(DocumentoService documentoService,
                               AgendamentoRepository agendamentoRepository) {
        this.documentoService = documentoService;
        this.agendamentoRepository = agendamentoRepository;
    }

    @GetMapping("/contratos/{agendamentoId}")
    @Operation(summary = "Baixar contrato em PDF")
    public ResponseEntity<byte[]> downloadContrato(@PathVariable UUID agendamentoId) {
        var pdf = documentoService.gerarContrato(agendamentoId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=contrato_" + agendamentoId + ".pdf")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf);
    }

    @GetMapping("/recibos/{agendamentoId}")
    @Operation(summary = "Baixar recibo em PDF")
    public ResponseEntity<byte[]> downloadRecibo(@PathVariable UUID agendamentoId) {
        var pdf = documentoService.gerarRecibo(agendamentoId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=recibo_" + agendamentoId + ".pdf")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf);
    }

    @GetMapping("/comprovantes/{agendamentoId}/{tipo}")
    @Operation(summary = "Servir comprovante de pagamento (entrada ou final)")
    public ResponseEntity<Resource> downloadComprovante(
            @PathVariable UUID agendamentoId,
            @PathVariable String tipo) {

        var tipoComprovante = TipoComprovante.fromValor(tipo);

        var agendamento = agendamentoRepository.findById(agendamentoId)
            .orElseThrow(() -> new AgendamentoNaoEncontradoException(agendamentoId));

        var caminho = tipoComprovante.extrairUrl(agendamento);

        if (!tipoComprovante.urlValida(caminho)) {
            return ResponseEntity.notFound().build();
        }

        var file = new FileSystemResource(Path.of(caminho));
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        var contentType = resolverContentType(caminho);
        var filename = "comprovante_" + tipo + "_" + agendamentoId + extensaoDoArquivo(caminho);

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
            .contentType(contentType)
            .body(file);
    }

    private MediaType resolverContentType(String caminho) {
        try {
            var probe = Files.probeContentType(Path.of(caminho));
            if (probe != null) {
                return MediaType.parseMediaType(probe);
            }
        } catch (Exception ignored) {
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private String extensaoDoArquivo(String caminho) {
        if (caminho == null) return ".bin";
        var nome = Path.of(caminho).getFileName().toString();
        var idx = nome.lastIndexOf('.');
        return idx >= 0 ? nome.substring(idx) : ".bin";
    }
}
