package com.photoizer.crm.fotografo.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.AgendamentoFotografo;
import com.photoizer.crm.agenda.model.RepasseStatus;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.despesa.model.Despesa;
import com.photoizer.crm.fotografo.api.FotografoDashboardResponse;
import com.photoizer.crm.fotografo.api.FotografoEnsaiosResponse;
import com.photoizer.crm.fotografo.api.FotografoRelatorioGlobalResponse;
import com.photoizer.crm.fotografo.api.FotografoResumoFinanceiroResponse;
import com.photoizer.crm.agenda.exception.FotografoNaoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * Service dedicado a consultas agregadas e relatórios de fotógrafos.
 *
 * Design Pattern: Query Service — separa operações de leitura complexas
 * (agregações, relatórios, dashboards) do service principal de CRUD.
 * Motivo: o FotografoService original (~271 linhas) misturava CRUD com
 * lógica de agregação (dashboard, resumoFinanceiro, relatorioGlobal).
 * Esta separação:
 * 1. Melhora coesão (SRP) — CRUD vs Queries
 * 2. Facilita otimização de queries SQL futura
 * 3. Permite caching independente por tipo de operação
 * 4. Reduz complexidade de cada service
 */
@Service
@Transactional(readOnly = true)
public class FotografoQueryService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final FotografoDataFacade dataFacade;

    public FotografoQueryService(FotografoDataFacade dataFacade) {
        this.dataFacade = dataFacade;
    }

    public FotografoDashboardResponse dashboard(UUID fotografoId) {
        var fotografo = dataFacade.findFotografoById(fotografoId)
            .orElseThrow(() -> new FotografoNaoEncontradoException(fotografoId));

        var links = dataFacade.findLinksByFotografoIdWithAgendamento(fotografoId);
        var ensaios = links.stream().map(AgendamentoFotografo::getAgendamento).toList();

        var totalValorCobrado = ensaios.stream()
            .map(Agendamento::getValorTotalFinal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        var totalCustosFotografo = links.stream()
            .map(l -> dataFacade.calcularCustosFotografo(l.getAgendamento().getId(), fotografoId))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        var totalRepasse = links.stream()
            .map(l -> l.getValorRepassar() != null ? l.getValorRepassar() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        var totalPartilha = ensaios.stream()
            .map(a -> a.getValorPartilhaGlobal() != null ? a.getValorPartilhaGlobal() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        var totalLucroCrm = ensaios.stream()
            .map(a -> a.getValorLucroCrm() != null ? a.getValorLucroCrm() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        var ultimosEnsaios = links.stream()
            .sorted((a, b) -> b.getAgendamento().getDataHoraEnsaio().compareTo(a.getAgendamento().getDataHoraEnsaio()))
            .limit(10)
            .map(this::toEnsaiosResponse)
            .toList();

        return new FotografoDashboardResponse(
            fotografoId, fotografo.getNome(),
            ensaios.size(), totalValorCobrado, totalCustosFotografo,
            totalPartilha, totalRepasse, totalLucroCrm,
            ultimosEnsaios
        );
    }

    public List<FotografoEnsaiosResponse> listarEnsaios(UUID fotografoId) {
        dataFacade.findFotografoById(fotografoId)
            .orElseThrow(() -> new FotografoNaoEncontradoException(fotografoId));

        return dataFacade.findLinksByFotografoIdWithAgendamento(fotografoId).stream()
            .map(this::toEnsaiosResponse)
            .toList();
    }

    public FotografoResumoFinanceiroResponse resumoFinanceiro(UUID fotografoId) {
        var fotografo = dataFacade.findFotografoById(fotografoId)
            .orElseThrow(() -> new FotografoNaoEncontradoException(fotografoId));

        var links = dataFacade.findLinksByFotografoIdWithAgendamento(fotografoId);
        var ensaios = links.stream().map(AgendamentoFotografo::getAgendamento).toList();

        var pendentes = (int) ensaios.stream()
            .filter(a -> a.getStatus() == StatusAgendamento.CONFIRMADO).count();
        var realizados = (int) ensaios.stream()
            .filter(a -> a.getStatus() == StatusAgendamento.REALIZADO).count();
        var finalizados = (int) ensaios.stream()
            .filter(a -> a.getStatus() == StatusAgendamento.FINALIZADO).count();

        var totalValorCobrado = ensaios.stream().map(Agendamento::getValorTotalFinal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        var totalPartilha = ensaios.stream()
            .map(a -> a.getValorPartilhaGlobal() != null ? a.getValorPartilhaGlobal() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        var totalRepasse = links.stream()
            .map(l -> l.getValorRepassar() != null ? l.getValorRepassar() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        var totalLucroCrm = ensaios.stream()
            .map(a -> a.getValorLucroCrm() != null ? a.getValorLucroCrm() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        var totalCustos = links.stream()
            .map(l -> dataFacade.calcularCustosFotografo(l.getAgendamento().getId(), fotografoId))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        var mediaPartilha = ensaios.isEmpty() ? BigDecimal.ZERO
            : totalPartilha.divide(BigDecimal.valueOf(ensaios.size()), 2, RoundingMode.HALF_UP);

        var despesas = dataFacade.findDespesasByFotografoId(fotografoId);
        var custosPorCategoria = new HashMap<String, BigDecimal>();
        for (var d : despesas) {
            var cat = d.getCategoria() != null ? d.getCategoria() : "Outros";
            custosPorCategoria.merge(cat, d.getValor(), BigDecimal::add);
        }

        var custosPorEnsaio = links.stream().map(l -> {
            var ag = l.getAgendamento();
            var custo = dataFacade.calcularCustosFotografo(ag.getId(), fotografoId);
            return new FotografoResumoFinanceiroResponse.CustoPorEnsaio(
                ag.getId(), ag.getCliente().getNome(),
                ag.getDataHoraEnsaio().format(DATE_FORMAT), custo
            );
        }).filter(c -> c.total().compareTo(BigDecimal.ZERO) > 0).toList();

        var totalRepassesPendentes = links.stream()
            .filter(l -> l.getStatus() == RepasseStatus.PENDENTE)
            .map(l -> l.getValorRepassar() != null ? l.getValorRepassar() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        var totalRepassesRealizados = links.stream()
            .filter(l -> l.getStatus() == RepasseStatus.PAGO)
            .map(l -> l.getValorRepassar() != null ? l.getValorRepassar() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new FotografoResumoFinanceiroResponse(
            fotografoId, fotografo.getNome(),
            ensaios.size(), pendentes, realizados, finalizados,
            totalValorCobrado, totalCustos, totalPartilha, totalRepasse, totalLucroCrm,
            mediaPartilha, totalRepassesPendentes, totalRepassesRealizados,
            custosPorCategoria, custosPorEnsaio
        );
    }

    public FotografoRelatorioGlobalResponse relatorioGlobal() {
        var fotografos = dataFacade.findFotografos();
        var items = new ArrayList<FotografoRelatorioGlobalResponse.FotografoItem>();

        var totalCustos = BigDecimal.ZERO;
        var totalRepasse = BigDecimal.ZERO;
        var ensaiosUnicos = new LinkedHashMap<UUID, Agendamento>();

        for (var f : fotografos) {
            var links = dataFacade.findLinksByFotografoIdWithAgendamento(f.getId());
            var ensaios = links.stream().map(AgendamentoFotografo::getAgendamento).toList();

            var valorCobrado = ensaios.stream()
                .map(Agendamento::getValorTotalFinal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            var custosFotografo = links.stream()
                .map(l -> dataFacade.calcularCustosFotografo(l.getAgendamento().getId(), f.getId()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            var repasse = links.stream()
                .map(l -> l.getValorRepassar() != null ? l.getValorRepassar() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            var partilha = ensaios.stream()
                .map(a -> a.getValorPartilhaGlobal() != null ? a.getValorPartilhaGlobal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            var lucroCrm = ensaios.stream()
                .map(a -> a.getValorLucroCrm() != null ? a.getValorLucroCrm() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            items.add(new FotografoRelatorioGlobalResponse.FotografoItem(
                f.getNome(), ensaios.size(),
                valorCobrado, custosFotografo,
                partilha, repasse, lucroCrm
            ));
            totalCustos = totalCustos.add(custosFotografo);
            totalRepasse = totalRepasse.add(repasse);

            for (var link : links) {
                ensaiosUnicos.putIfAbsent(link.getAgendamento().getId(), link.getAgendamento());
            }
        }

        var ensaios = ensaiosUnicos.values();
        var totalEnsaios = ensaios.size();
        var totalValorCobrado = ensaios.stream().map(Agendamento::getValorTotalFinal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        var totalPartilha = ensaios.stream()
            .map(a -> a.getValorPartilhaGlobal() != null ? a.getValorPartilhaGlobal() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        var totalLucroCrm = ensaios.stream()
            .map(a -> a.getValorLucroCrm() != null ? a.getValorLucroCrm() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new FotografoRelatorioGlobalResponse(
            fotografos.size(), totalEnsaios, totalValorCobrado, totalCustos,
            totalPartilha, totalRepasse, totalLucroCrm, items
        );
    }

    private FotografoEnsaiosResponse toEnsaiosResponse(AgendamentoFotografo link) {
        var a = link.getAgendamento();
        var fotografoId = link.getFotografo().getId();
        var custos = dataFacade.calcularCustosFotografo(a.getId(), fotografoId);
        return new FotografoEnsaiosResponse(
            a.getId(),
            a.getCliente().getNome(),
            a.getPacote() != null ? a.getPacote().getNome() : null,
            a.getDataHoraEnsaio(),
            a.getStatus().name(),
            a.getValorTotalFinal(),
            custos,
            a.getValorPartilhaGlobal() != null ? a.getValorPartilhaGlobal() : BigDecimal.ZERO,
            link.getValorRepassar() != null ? link.getValorRepassar() : BigDecimal.ZERO,
            a.getValorLucroCrm() != null ? a.getValorLucroCrm() : BigDecimal.ZERO
        );
    }
}
