package com.photoizer.crm.agenda.security;

import com.photoizer.crm.agenda.api.AgendamentoResponse;
import com.photoizer.crm.agenda.model.RepasseStatus;
import com.photoizer.crm.auth.model.Papel;
import com.photoizer.crm.shared.model.FormaPagamento;
import com.photoizer.crm.shared.model.TipoRepasse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garante que a resposta de agendamento não exponha métricas internas do estúdio
 * (custos, comissões, partilha, lucro e repasses) quando redigida para papéis sem
 * visibilidade financeira (AGENDADOR), preservando os valores de atendimento ao
 * cliente (pacote, entrada, saldo, total).
 */
class AgendamentoResponseRedacaoTest {

    @Test
    @DisplayName("ocultarDadosInternos remove métricas internas e preserva valores do cliente")
    void deveOcultarMetricasInternas() {
        var fotografo = new AgendamentoResponse.FotografoNoAgendamento(
            UUID.randomUUID(), "Fotógrafo", new BigDecimal("500.00"), RepasseStatus.PENDENTE,
            null, TipoRepasse.FIXO, null, Papel.FOTOGRAFO);

        var original = novoResponse(List.of(fotografo));

        var redigido = original.ocultarDadosInternos();

        // Valores de atendimento ao cliente permanecem
        assertThat(redigido.valorTotal()).isEqualByComparingTo(original.valorTotal());
        assertThat(redigido.valorPacote()).isEqualByComparingTo(original.valorPacote());
        assertThat(redigido.valorEntradaExigido()).isEqualByComparingTo(original.valorEntradaExigido());
        assertThat(redigido.saldoDevedor()).isEqualByComparingTo(original.saldoDevedor());

        // Métricas internas ocultas
        assertThat(redigido.custoDeslocamento()).isNull();
        assertThat(redigido.valorPartilhaGlobal()).isNull();
        assertThat(redigido.valorLucroCrm()).isNull();
        assertThat(redigido.valorComissao()).isNull();
        assertThat(redigido.indicadorNome()).isNull();
        assertThat(redigido.statusComissao()).isNull();

        // Repasses dos parceiros ocultos
        assertThat(redigido.fotografos()).hasSize(1);
        var f = redigido.fotografos().getFirst();
        assertThat(f.valorRepassar()).isNull();
        assertThat(f.tipoValor()).isNull();
        assertThat(f.percentual()).isNull();
        // O parceiro continua visível, apenas sem valores
        assertThat(f.fotografoId()).isEqualTo(fotografo.fotografoId());
        assertThat(f.fotografoNome()).isEqualTo("Fotógrafo");
    }

    @Test
    @DisplayName("ocultarDadosInternos funciona mesmo sem parceiros vinculados")
    void deveOcultarMetricasSemParceiros() {
        var redigido = novoResponse(null).ocultarDadosInternos();

        assertThat(redigido.custoDeslocamento()).isNull();
        assertThat(redigido.valorLucroCrm()).isNull();
        assertThat(redigido.valorComissao()).isNull();
        assertThat(redigido.fotografos()).isNull();
        assertThat(redigido.valorTotal()).isNotNull();
    }

    private AgendamentoResponse novoResponse(List<AgendamentoResponse.FotografoNoAgendamento> fotografos) {
        return new AgendamentoResponse(
            UUID.randomUUID(), UUID.randomUUID(), "Cliente", "11999999999", "c@x.com", null,
            "São Paulo", "SP", UUID.randomUUID(), "Pacote", "#64748B", null, null,
            null, null, LocalDateTime.now(), 60, "Estúdio",
            new BigDecimal("1000.00"), new BigDecimal("300.00"), new BigDecimal("300.00"), new BigDecimal("700.00"),
            BigDecimal.ZERO, new BigDecimal("50.00"), new BigDecimal("40.00"), true,
            new BigDecimal("1000.00"), new BigDecimal("30"), new BigDecimal("800.00"), new BigDecimal("700.00"),
            "CONFIRMADO", null, null, null, null,
            false, false, FormaPagamento.DINHEIRO,
            true, null, "token", null,
            null, false, false, null, null,
            null, null, null, LocalDateTime.now(), LocalDateTime.now(),
            fotografos, new BigDecimal("400.00"), new BigDecimal("200.00"), new BigDecimal("150.00"),
            "Indicador X", "PENDENTE");
    }
}
