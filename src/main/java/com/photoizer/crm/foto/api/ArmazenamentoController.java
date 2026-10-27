package com.photoizer.crm.foto.api;

import com.photoizer.crm.foto.service.ArmazenamentoService;
import com.photoizer.crm.foto.service.ArquivosOrfaosService;
import com.photoizer.crm.foto.service.ExpurgoGaleriaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

import java.util.List;
import java.util.UUID;

/**
 * Painel de armazenamento: mede o consumo de disco das galerias e informa
 * quais ensaios finalizados podem ter os arquivos expurgados.
 *
 * <p>Somente ADMIN: a operação de expurgo é irreversível e apaga arquivos de
 * todas as fotos da galeria escolhida.
 */
@RestController
@RequestMapping("/api/v1/admin/armazenamento")
@RolesAllowed("ADMIN")
@Tag(name = "Armazenamento", description = "Consumo de disco e expurgo de arquivos das galerias")
public class ArmazenamentoController {

    private final ArmazenamentoService armazenamentoService;
    private final ExpurgoGaleriaService expurgoGaleriaService;
    private final ArquivosOrfaosService arquivosOrfaosService;

    public ArmazenamentoController(ArmazenamentoService armazenamentoService,
                                   ExpurgoGaleriaService expurgoGaleriaService,
                                   ArquivosOrfaosService arquivosOrfaosService) {
        this.armazenamentoService = armazenamentoService;
        this.expurgoGaleriaService = expurgoGaleriaService;
        this.arquivosOrfaosService = arquivosOrfaosService;
    }

    @GetMapping("/resumo")
    @Operation(
        summary = "Resumo do consumo de disco",
        description = "Total do diretório de uploads por tipo de arquivo, espaço livre do volume "
            + "e totais de fotos/galerias arquivadas.")
    public ResponseEntity<ArmazenamentoResumoResponse> resumo() {
        return ResponseEntity.ok(armazenamentoService.resumo());
    }

    @GetMapping("/galerias")
    @Operation(
        summary = "Listar galerias por consumo de disco",
        description = "Retorna as galerias com fotos ou arquivos em disco, ordenadas do maior para "
            + "o menor consumo, com a elegibilidade do expurgo.")
    public ResponseEntity<List<GaleriaArmazenamentoResponse>> galerias() {
        return ResponseEntity.ok(armazenamentoService.galerias());
    }

    @GetMapping("/galerias/{agendamentoId}")
    @Operation(summary = "Consultar o consumo de uma galeria")
    public ResponseEntity<GaleriaArmazenamentoResponse> galeria(@PathVariable UUID agendamentoId) {
        return ResponseEntity.ok(armazenamentoService.galeria(agendamentoId));
    }

    @GetMapping("/galerias/{agendamentoId}/manifesto")
    @Operation(
        summary = "Baixar o manifesto da galeria em CSV",
        description = "Lista número, id, nome, status e bytes liberados das fotos. Use antes do expurgo "
            + "para guardar a referência do que será removido.")
    public ResponseEntity<byte[]> manifesto(@PathVariable UUID agendamentoId) {
        var csv = armazenamentoService.manifesto(agendamentoId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"manifesto-galeria-" + agendamentoId + ".csv\"")
            .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
            .body(csv);
    }

    @PostMapping("/galerias/{agendamentoId}/expurgar")    @Operation(
        summary = "Expurgar os arquivos de uma galeria finalizada",
        description = "Remove original, derivados, versões editadas e caches de TODAS as fotos da "
            + "galeria escolhida. Os registros e o histórico comercial são preservados; comprovantes "
            + "e contratos não são afetados. A ação é irreversível.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Arquivos expurgados"),
        @ApiResponse(responseCode = "404", description = "Agendamento não encontrado", content = @Content),
        @ApiResponse(responseCode = "422", description = "Expurgo não permitido no estado atual",
            content = @Content)
    })
    public ResponseEntity<ExpurgarGaleriaResponse> expurgar(@PathVariable UUID agendamentoId) {
        return ResponseEntity.ok(expurgoGaleriaService.expurgar(agendamentoId));
    }

    @GetMapping("/orfaos")
    @Operation(
        summary = "Listar arquivos órfãos",
        description = "Compara o diretório de uploads com os caminhos referenciados pelos módulos. "
            + "Nomes desconhecidos são listados, mas nunca elegíveis para remoção.")
    public ResponseEntity<ArquivosOrfaosResponse> orfaos() {
        return ResponseEntity.ok(arquivosOrfaosService.listar());
    }

    @PostMapping("/orfaos/limpar")
    @Operation(
        summary = "Remover arquivos órfãos elegíveis",
        description = "Apaga somente arquivos com nome reconhecido e mais de 24h sem referência no banco. "
            + "Comprovantes, contratos e arquivos desconhecidos são preservados.")
    public ResponseEntity<LimparOrfaosResponse> limparOrfaos() {
        return ResponseEntity.ok(arquivosOrfaosService.limpar());
    }
}
