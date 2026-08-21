package com.photoizer.crm.comissao.service;

import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.comissao.api.ConsultaComissoesResponse;
import com.photoizer.crm.comissao.api.IndicacaoResponse;
import com.photoizer.crm.comissao.api.IndicadorResumoResponse;
import com.photoizer.crm.comissao.model.Indicacao;
import com.photoizer.crm.comissao.model.OrigemIndicacao;
import com.photoizer.crm.comissao.model.StatusIndicacao;
import com.photoizer.crm.comissao.repository.IndicacaoRepository;
import com.photoizer.crm.indicador.model.Indicador;
import com.photoizer.crm.indicador.repository.IndicadorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class IndicacaoService {

    private final IndicacaoRepository indicacaoRepository;
    private final ComissaoCalculator comissaoCalculator;
    private final AgendamentoRepository agendamentoRepository;
    private final IndicadorRepository indicadorRepository;

    public IndicacaoService(IndicacaoRepository indicacaoRepository,
                            ComissaoCalculator comissaoCalculator,
                            AgendamentoRepository agendamentoRepository,
                            IndicadorRepository indicadorRepository) {
        this.indicacaoRepository = indicacaoRepository;
        this.comissaoCalculator = comissaoCalculator;
        this.agendamentoRepository = agendamentoRepository;
        this.indicadorRepository = indicadorRepository;
    }

    public Indicacao criar(UUID agendamentoId, UUID indicadorId, String indicadorNome, String indicadorTelefone,
                           OrigemIndicacao origem, BigDecimal percentual, BigDecimal valorReferencia) {
        var comissao = comissaoCalculator.calcular(valorReferencia, percentual);

        var indicacao = Indicacao.builder()
            .agendamentoId(agendamentoId)
            .indicadorId(indicadorId)
            .indicadorNome(indicadorNome)
            .indicadorTelefone(indicadorTelefone)
            .origem(origem)
            .percentual(percentual)
            .valorReferencia(valorReferencia)
            .valorComissao(comissao)
            .status(StatusIndicacao.PENDENTE)
            .build();

        return indicacaoRepository.save(indicacao);
    }

    public void marcarTodasComoPaga(UUID agendamentoId) {
        var indicacoes = indicacaoRepository.findAllByAgendamentoId(agendamentoId);
        indicacoes.stream()
            .filter(i -> i.getStatus() == StatusIndicacao.PENDENTE)
            .forEach(Indicacao::pagar);
        indicacaoRepository.saveAll(indicacoes);
    }

    public void marcarTodasComoCancelada(UUID agendamentoId) {
        var indicacoes = indicacaoRepository.findAllByAgendamentoId(agendamentoId);
        indicacoes.stream()
            .filter(i -> i.getStatus() == StatusIndicacao.PENDENTE)
            .forEach(Indicacao::cancelar);
        indicacaoRepository.saveAll(indicacoes);
    }

    @Transactional(readOnly = true)
    public List<Indicacao> consultarPorTelefone(String telefone) {
        return indicacaoRepository.findByIndicadorTelefoneOrderByCreatedAtDesc(telefone);
    }

    /**
     * Consulta comissões por telefone do indicador, enriquecendo com dados
     * do agendamento e calculando totais. Substitui a lógica que estava no controller.
     */
    @Transactional(readOnly = true)
    public ConsultaComissoesResponse consultarComAgendamento(String telefone) {
        var indicacoes = indicacaoRepository.findByIndicadorTelefoneOrderByCreatedAtDesc(telefone);

        var agendamentoIds = indicacoes.stream()
            .map(Indicacao::getAgendamentoId)
            .toList();

        var agendamentos = agendamentoRepository.findAllById(agendamentoIds);
        var agendamentoMap = agendamentos.stream()
            .collect(java.util.stream.Collectors.toMap(
                com.photoizer.crm.agenda.model.Agendamento::getId, a -> a));

        var responses = indicacoes.stream().map(i -> {
            var agendamento = agendamentoMap.get(i.getAgendamentoId());
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
            .filter(r -> r.status() == StatusIndicacao.PENDENTE)
            .map(IndicacaoResponse::valorComissao)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        var totalPago = responses.stream()
            .filter(r -> r.status() == StatusIndicacao.PAGA)
            .map(IndicacaoResponse::valorComissao)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        var indicadorNome = indicacoes.isEmpty() ? "" : indicacoes.get(0).getIndicadorNome();

        return new ConsultaComissoesResponse(
            indicadorNome,
            telefone,
            totalPendente,
            totalPago,
            responses
        );
    }

    /**
     * Lista resumo de indicadores com totais de comissões.
     * Usa query agregada (GROUP BY) para evitar N+1.
     * Complementa com indicadores cadastrados que não têm comissões.
     */
    @Transactional(readOnly = true)
    public List<IndicadorResumoResponse> listarResumoIndicadores() {
        var projetoes = indicacaoRepository.findIndicadoresComResumo();
        var resultado = new ArrayList<IndicadorResumoResponse>();

        var telefonesComComissao = new java.util.HashSet<String>();

        for (var p : projetoes) {
            telefonesComComissao.add(p.getTelefone());

            var percentual = indicadorRepository.findByTelefone(p.getTelefone())
                .stream().findFirst()
                .map(Indicador::getPercentualComissao)
                .orElse(null);

            resultado.add(new IndicadorResumoResponse(
                p.getIndicadorId(),
                p.getNome(),
                p.getTelefone(),
                p.getTotalPendente(),
                p.getTotalPago(),
                p.getTotalCancelado(),
                p.getTotalIndicacoes(),
                percentual
            ));
        }

        var todosIndicadoresCadastrados = indicadorRepository.findAll();
        for (var indicador : todosIndicadoresCadastrados) {
            if (!telefonesComComissao.contains(indicador.getTelefone())) {
                resultado.add(new IndicadorResumoResponse(
                    indicador.getId(),
                    indicador.getNome(),
                    indicador.getTelefone(),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    0,
                    indicador.getPercentualComissao()
                ));
            }
        }

        return resultado;
    }
}
