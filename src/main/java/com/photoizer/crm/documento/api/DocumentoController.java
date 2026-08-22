package com.photoizer.crm.documento.api;

import com.photoizer.crm.agenda.exception.AgendamentoNaoEncontradoException;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.documento.model.TipoComprovante;
import com.photoizer.crm.documento.service.DocumentoService;
import com.photoizer.crm.shared.storage.FileServeHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
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

    private final DocumentoService documentoService;
    private final AgendamentoRepository agendamentoRepository;
    private final FileServeHelper fileServeHelper;

    public DocumentoController(DocumentoService documentoService,
                               AgendamentoRepository agendamentoRepository,
                               FileServeHelper fileServeHelper) {
        this.documentoService = documentoService;
        this.agendamentoRepository = agendamentoRepository;
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

        var tipoComprovante = TipoComprovante.fromValor(tipo);

        var agendamento = agendamentoRepository.findById(agendamentoId)
            .orElseThrow(() -> new AgendamentoNaoEncontradoException(agendamentoId));

        var caminho = tipoComprovante.extrairUrl(agendamento);

        if (!tipoComprovante.urlValida(caminho)) {
            return ResponseEntity.notFound().build();
        }

        var filename = "comprovante_" + tipo + "_" + agendamentoId
            + fileServeHelper.extrairExtensao(caminho);

        return fileServeHelper.servirArquivo(caminho, filename, "attachment");
    }
}
