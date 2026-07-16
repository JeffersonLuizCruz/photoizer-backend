package com.photoizer.crm.documento.api;

import com.photoizer.crm.documento.service.ContratoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documentos")
@Tag(name = "Documentos", description = "Geração de contratos e recibos")
public class DocumentoController {

    private final ContratoService contratoService;

    public DocumentoController(ContratoService contratoService) {
        this.contratoService = contratoService;
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
}
