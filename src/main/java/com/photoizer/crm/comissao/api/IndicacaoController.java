package com.photoizer.crm.comissao.api;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.comissao.model.Indicacao;
import com.photoizer.crm.comissao.repository.IndicacaoRepository;
import com.photoizer.crm.comissao.service.IndicacaoService;
import com.photoizer.crm.indicador.repository.IndicadorRepository;
import com.photoizer.crm.indicador.service.IndicadorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/comissoes")
@Tag(name = "Comissões", description = "Consulta de comissões por indicação")
public class IndicacaoController {

    private final IndicacaoService indicacaoService;
    private final IndicacaoRepository indicacaoRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final IndicadorService indicadorService;
    private final IndicadorRepository indicadorRepository;

    public IndicacaoController(IndicacaoService indicacaoService,
                                IndicacaoRepository indicacaoRepository,
                                AgendamentoRepository agendamentoRepository,
                                IndicadorService indicadorService,
                                IndicadorRepository indicadorRepository) {
        this.indicacaoService = indicacaoService;
        this.indicacaoRepository = indicacaoRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.indicadorService = indicadorService;
        this.indicadorRepository = indicadorRepository;
    }

    @GetMapping("/consulta")
    @Operation(summary = "Consultar comissões por telefone do indicador")
    public ResponseEntity<Map<String, Object>> consultar(@RequestParam String telefone) {
        var indicacoes = indicacaoService.consultarPorTelefone(telefone);

        var agendamentoIds = indicacoes.stream()
            .map(Indicacao::getAgendamentoId)
            .toList();

        var agendamentos = agendamentoRepository.findAllById(agendamentoIds).stream()
            .collect(Collectors.toMap(Agendamento::getId, a -> a));

        var responses = indicacoes.stream().map(i -> {
            var agendamento = agendamentos.get(i.getAgendamentoId());
            if (agendamento == null) return null;
            return IndicacaoResponse.of(i,
                agendamento.getCliente().getNome(),
                agendamento.getPacote().getNome(),
                agendamento.getValorTotalFinal(),
                agendamento.getValorExtras(),
                agendamento.getDataHoraEnsaio()
            );
        }).filter(r -> r != null).toList();

        var totalPendente = responses.stream()
            .filter(r -> "PENDENTE".equals(r.status()))
            .map(IndicacaoResponse::valorComissao)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        var totalPago = responses.stream()
            .filter(r -> "PAGA".equals(r.status()))
            .map(IndicacaoResponse::valorComissao)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        var indicador = indicacoes.isEmpty() ? null : indicacoes.get(0);

        return ResponseEntity.ok(Map.of(
            "indicadorNome", indicador != null ? indicador.getIndicadorNome() : "",
            "indicadorTelefone", telefone,
            "totalPendente", totalPendente,
            "totalPago", totalPago,
            "indicacoes", responses
        ));
    }

    @GetMapping("/indicadores")
    @Operation(summary = "Listar todos os indicadores com resumo de comissões")
    public ResponseEntity<List<Map<String, Object>>> listarIndicadores() {
        var todosTelefones = indicacaoRepository.findAllDistinctTelefones();
        var indicadorCache = new HashMap<String, BigDecimal>();
        var resultado = new ArrayList<Map<String, Object>>();
        var telefonesComComissao = new java.util.HashSet<String>();

        for (var telefone : todosTelefones) {
            var indicacoes = indicacaoRepository.findByIndicadorTelefoneOrderByCreatedAtDesc(telefone);
            if (indicacoes.isEmpty()) continue;

            telefonesComComissao.add(telefone);

            var primeira = indicacoes.get(0);
            var totalPendente = indicacoes.stream()
                .filter(i -> "PENDENTE".equals(i.getStatus()))
                .map(Indicacao::getValorComissao)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            var totalPago = indicacoes.stream()
                .filter(i -> "PAGA".equals(i.getStatus()))
                .map(Indicacao::getValorComissao)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            var totalCancelado = indicacoes.stream()
                .filter(i -> "CANCELADA".equals(i.getStatus()))
                .map(Indicacao::getValorComissao)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            var percentualComissao = indicadorCache.computeIfAbsent(telefone, t -> {
                var list = indicadorRepository.findByTelefone(t);
                return list.isEmpty() ? null : list.getFirst().getPercentualComissao();
            });

            var entry = new HashMap<String, Object>();
            entry.put("indicadorId", primeira.getIndicadorId() != null ? primeira.getIndicadorId().toString() : null);
            entry.put("indicadorNome", primeira.getIndicadorNome());
            entry.put("indicadorTelefone", primeira.getIndicadorTelefone());
            entry.put("totalPendente", totalPendente);
            entry.put("totalPago", totalPago);
            entry.put("totalCancelado", totalCancelado);
            entry.put("totalIndicacoes", indicacoes.size());
            entry.put("percentualComissao", percentualComissao);
            resultado.add(entry);
        }

        // Adicionar indicadores cadastrados que NÃO têm comissões ainda
        var todosIndicadoresCadastrados = indicadorRepository.findAll();
        for (var indicador : todosIndicadoresCadastrados) {
            if (!telefonesComComissao.contains(indicador.getTelefone())) {
                var entry = new HashMap<String, Object>();
                entry.put("indicadorId", indicador.getId().toString());
                entry.put("indicadorNome", indicador.getNome());
                entry.put("indicadorTelefone", indicador.getTelefone());
                entry.put("totalPendente", BigDecimal.ZERO);
                entry.put("totalPago", BigDecimal.ZERO);
                entry.put("totalCancelado", BigDecimal.ZERO);
                entry.put("totalIndicacoes", 0);
                entry.put("percentualComissao", indicador.getPercentualComissao());
                resultado.add(entry);
            }
        }

        return ResponseEntity.ok(resultado);
    }
}
