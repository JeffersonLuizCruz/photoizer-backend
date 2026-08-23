package com.photoizer.crm.documento.api;

import com.photoizer.crm.documento.service.DocumentoService;
import com.photoizer.crm.shared.storage.FileServeHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller do modulo documento.
 *
 * PATTERN: Facade (via DocumentoService)
 * O controller delega toda logica de dominio para DocumentoService,
 * incluindo a resolucao de comprovantes. Isso elimina a dependencia
 * direta de AgendamentoRepository (violacao de modulo).
 *
 * PATTERN: Role-Based Access Control
 * @RolesAllowed garante que apenas ADMIN e FOTOGRAFO acessem os endpoints.
 * Antes: qualquer autenticado podia baixar contratos/recibos/comprovantes.
 */
@RestController
@RequestMapping("/api/v1/documentos")
@Tag(name = "Documentos", description = "Geracao de contratos e recibos")
@RolesAllowed({"ADMIN", "FOTOGRAFO"})
public class DocumentoController {

    private final DocumentoService documentoService;
    private final FileServeHelper fileServeHelper;

    public DocumentoController(DocumentoService documentoService,
                               FileServeHelper fileServeHelper) {
        this.documentoService = documentoService;
        this.fileServeHelper = fileServeHelper;
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

        var comprovante = documentoService.resolverComprovante(agendamentoId, tipo);

        if (comprovante == null) {
            return ResponseEntity.notFound().build();
        }

        var filename = "comprovante_" + comprovante.tipo() + "_" + comprovante.agendamentoId()
            + fileServeHelper.extrairExtensao(comprovante.caminho());

        return fileServeHelper.servirArquivo(comprovante.caminho(), filename, "attachment");
    }
}
