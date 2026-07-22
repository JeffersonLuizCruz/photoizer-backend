package com.photoizer.crm.comissao.service;

import com.photoizer.crm.comissao.model.Indicacao;
import com.photoizer.crm.comissao.repository.IndicacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class IndicacaoService {

    private final IndicacaoRepository indicacaoRepository;

    public IndicacaoService(IndicacaoRepository indicacaoRepository) {
        this.indicacaoRepository = indicacaoRepository;
    }

    public Indicacao criar(UUID agendamentoId, UUID indicadorId, String indicadorNome, String indicadorTelefone,
                           String origem, BigDecimal percentual, BigDecimal valorReferencia) {
        var comissao = valorReferencia.multiply(percentual).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        var indicacao = Indicacao.builder()
            .agendamentoId(agendamentoId)
            .indicadorId(indicadorId)
            .indicadorNome(indicadorNome)
            .indicadorTelefone(indicadorTelefone)
            .origem(origem)
            .percentual(percentual)
            .valorReferencia(valorReferencia)
            .valorComissao(comissao)
            .status("PENDENTE")
            .build();

        return indicacaoRepository.save(indicacao);
    }

    public void marcarTodasComoPaga(UUID agendamentoId) {
        var indicacoes = indicacaoRepository.findAllByAgendamentoId(agendamentoId);
        for (var i : indicacoes) {
            if ("PENDENTE".equals(i.getStatus())) {
                i.setStatus("PAGA");
                i.setDataPagamento(LocalDateTime.now());
            }
        }
        indicacaoRepository.saveAll(indicacoes);
    }

    public void marcarTodasComoCancelada(UUID agendamentoId) {
        var indicacoes = indicacaoRepository.findAllByAgendamentoId(agendamentoId);
        for (var i : indicacoes) {
            if ("PENDENTE".equals(i.getStatus())) {
                i.setStatus("CANCELADA");
            }
        }
        indicacaoRepository.saveAll(indicacoes);
    }

    @Transactional(readOnly = true)
    public List<Indicacao> consultarPorTelefone(String telefone) {
        return indicacaoRepository.findByIndicadorTelefoneOrderByCreatedAtDesc(telefone);
    }
}
