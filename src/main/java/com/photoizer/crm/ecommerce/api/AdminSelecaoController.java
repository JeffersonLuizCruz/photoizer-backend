package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.ecommerce.service.SelecaoDownloadService;
import com.photoizer.crm.ecommerce.service.SelecaoEnsaioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RolesAllowed({"ADMIN", "FOTOGRAFO", "EDITOR"})
@Tag(name = "Admin Seleções", description = "Seleções enviadas pelo cliente e edição das fotos")
public class AdminSelecaoController {

    private final SelecaoEnsaioService selecaoService;
    private final SelecaoDownloadService downloadService;

    public AdminSelecaoController(SelecaoEnsaioService selecaoService,
                                  SelecaoDownloadService downloadService) {
        this.selecaoService = selecaoService;
        this.downloadService = downloadService;
    }

    @GetMapping("/selecoes/pendentes")
    @Operation(summary = "Listar seleções aguardando edição")
    public ResponseEntity<List<SelecaoPendenteResponse>> pendentes() {
        return ResponseEntity.ok(selecaoService.listarPendentes());
    }

    @GetMapping("/agendamentos/{agendamentoId}/selecoes")
    @Operation(summary = "Listar o histórico de seleções do agendamento")
    public ResponseEntity<List<SelecaoEnsaioResponse>> listar(@PathVariable UUID agendamentoId) {
        return ResponseEntity.ok(selecaoService.listarDoAgendamento(agendamentoId));
    }

    @GetMapping("/agendamentos/{agendamentoId}/selecoes/{selecaoId}")
    @Operation(summary = "Detalhar a seleção com número e nome de cada foto")
    public ResponseEntity<SelecaoEnsaioResponse> detalhar(
            @PathVariable UUID agendamentoId,
            @PathVariable UUID selecaoId) {
        return ResponseEntity.ok(selecaoService.detalhar(agendamentoId, selecaoId));
    }

    @PostMapping("/agendamentos/{agendamentoId}/selecoes/{selecaoId}/iniciar")
    @Operation(summary = "Marcar a seleção como em edição")
    public ResponseEntity<SelecaoEnsaioResponse> iniciar(
            @PathVariable UUID agendamentoId,
            @PathVariable UUID selecaoId) {
        return ResponseEntity.ok(selecaoService.iniciar(agendamentoId, selecaoId));
    }

    @PostMapping("/agendamentos/{agendamentoId}/selecoes/{selecaoId}/reabrir")
    @RolesAllowed({"ADMIN", "FOTOGRAFO"})
    @Operation(summary = "Reabrir a seleção para o cliente ajustar")
    public ResponseEntity<SelecaoEnsaioResponse> reabrir(
            @PathVariable UUID agendamentoId,
            @PathVariable UUID selecaoId) {
        return ResponseEntity.ok(selecaoService.reabrir(agendamentoId, selecaoId));
    }

    @PostMapping("/agendamentos/{agendamentoId}/selecoes/{selecaoId}/publicar")
    @Operation(summary = "Publicar a edição e liberar a comercialização")
    public ResponseEntity<SelecaoEnsaioResponse> publicar(
            @PathVariable UUID agendamentoId,
            @PathVariable UUID selecaoId) {
        return ResponseEntity.ok(selecaoService.publicar(agendamentoId, selecaoId));
    }

    @GetMapping("/agendamentos/{agendamentoId}/selecoes/{selecaoId}/download-zip")
    @Operation(summary = "Baixar os originais da seleção para edição")
    public ResponseEntity<StreamingResponseBody> downloadZip(
            @PathVariable UUID agendamentoId,
            @PathVariable UUID selecaoId) {
        var zip = downloadService.preparar(agendamentoId, selecaoId);
        StreamingResponseBody body = outputStream -> {
            try {
                downloadService.escreverZip(zip, outputStream);
            } catch (IOException e) {
                throw new IllegalStateException("Falha ao gerar ZIP da seleção", e);
            }
        };
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + zip.fileName() + "\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(body);
    }
}
