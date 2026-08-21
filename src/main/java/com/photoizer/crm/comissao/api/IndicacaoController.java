package com.photoizer.crm.comissao.api;

import com.photoizer.crm.comissao.service.IndicacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller slim — apenas 1 dependência (facade via IndicacaoService).
 *
 * Pattern: Facade — toda orquestração (busca de agendamentos, soma de totais,
 * queries agregadas) é delegada ao IndicacaoService que atua como facade
 * interna. Isso elimina as 5 dependências originais (de 4 módulos diferentes)
 * e resolve a violação Modulith do controller.
 */
@RestController
@RequestMapping("/api/v1/comissoes")
@Tag(name = "Comissões", description = "Consulta de comissões por indicação")
public class IndicacaoController {

    private final IndicacaoService indicacaoService;

    public IndicacaoController(IndicacaoService indicacaoService) {
        this.indicacaoService = indicacaoService;
    }

    @GetMapping("/consulta")
    @Operation(summary = "Consultar comissões por telefone do indicador")
    public ResponseEntity<ConsultaComissoesResponse> consultar(@RequestParam String telefone) {
        return ResponseEntity.ok(indicacaoService.consultarComAgendamento(telefone));
    }

    @GetMapping("/indicadores")
    @Operation(summary = "Listar todos os indicadores com resumo de comissões")
    public ResponseEntity<List<IndicadorResumoResponse>> listarIndicadores() {
        return ResponseEntity.ok(indicacaoService.listarResumoIndicadores());
    }
}
