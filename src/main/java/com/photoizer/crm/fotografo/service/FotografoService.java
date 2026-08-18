package com.photoizer.crm.fotografo.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.AgendamentoFotografo;
import com.photoizer.crm.agenda.model.RepasseStatus;
import com.photoizer.crm.agenda.repository.AgendamentoFotografoRepository;
import com.photoizer.crm.auth.model.Papel;
import com.photoizer.crm.auth.model.User;
import com.photoizer.crm.auth.repository.UserRepository;
import com.photoizer.crm.despesa.model.Despesa;
import com.photoizer.crm.despesa.repository.DespesaRepository;
import com.photoizer.crm.fotografo.api.AtualizarFotografoRequest;
import com.photoizer.crm.fotografo.api.CriarFotografoRequest;
import com.photoizer.crm.fotografo.api.FotografoDashboardResponse;
import com.photoizer.crm.fotografo.api.FotografoEnsaiosResponse;
import com.photoizer.crm.fotografo.api.FotografoRelatorioGlobalResponse;
import com.photoizer.crm.fotografo.api.FotografoResumoFinanceiroResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class FotografoService {

    private final UserRepository userRepository;
    private final AgendamentoFotografoRepository agendamentoFotografoRepository;
    private final DespesaRepository despesaRepository;
    private final PasswordEncoder passwordEncoder;

    public FotografoService(UserRepository userRepository,
                            AgendamentoFotografoRepository agendamentoFotografoRepository,
                            DespesaRepository despesaRepository,
                            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.agendamentoFotografoRepository = agendamentoFotografoRepository;
        this.despesaRepository = despesaRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<User> listarFotografos() {
        return userRepository.findByPapel(Papel.FOTOGRAFO);
    }

    public List<User> listarParceiros() {
        var papéis = List.of(Papel.FOTOGRAFO, Papel.EDITOR, Papel.AGENDADOR);
        return papéis.stream()
            .map(userRepository::findByPapel)
            .flatMap(List::stream)
            .distinct()
            .toList();
    }

    @Transactional
    public User criar(CriarFotografoRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Já existe um usuário com este email: " + request.email());
        }
        var user = new User(
            request.email(),
            passwordEncoder.encode(request.senha()),
            request.nome(),
            Papel.FOTOGRAFO
        );
        if (request.telefone() != null && !request.telefone().isBlank()) {
            user.setTelefone(request.telefone());
        }
        return userRepository.save(user);
    }

    @Transactional
    public User atualizar(UUID id, AtualizarFotografoRequest request) {
        var user = userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Fotógrafo não encontrado: " + id));
        user.setNome(request.nome());
        user.setEmail(request.email());
        if (request.telefone() != null) {
            user.setTelefone(request.telefone());
        }
        return userRepository.save(user);
    }

    @Transactional
    public void toggleStatus(UUID id) {
        var user = userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Fotógrafo não encontrado: " + id));
        user.setAtivo(!user.isAtivo());
        userRepository.save(user);
    }

    @Transactional
    public void remover(UUID id) {
        var user = userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Fotógrafo não encontrado: " + id));
        var links = agendamentoFotografoRepository.findByFotografoIdOrderByAgendamentoDataHoraEnsaioDesc(id);
        if (!links.isEmpty()) {
            throw new IllegalArgumentException(
                "Fotógrafo possui " + links.size() + " ensaio(s) vinculado(s). Desative-o em vez de remover.");
        }
        userRepository.delete(user);
    }

    public FotografoDashboardResponse dashboard(UUID fotografoId) {
        var fotografo = userRepository.findById(fotografoId)
            .orElseThrow(() -> new IllegalArgumentException("Fotógrafo não encontrado: " + fotografoId));

        var links = agendamentoFotografoRepository.findByFotografoIdWithAgendamento(fotografoId);
        var ensaios = links.stream().map(AgendamentoFotografo::getAgendamento).toList();

        var totalValorCobrado = ensaios.stream()
            .map(Agendamento::getValorTotalFinal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        var totalCustosFotografo = links.stream()
            .map(l -> calcularCustosFotografo(l.getAgendamento().getId(), fotografoId))
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
            .map(l -> toEnsaiosResponse(l))
            .toList();

        return new FotografoDashboardResponse(
            fotografoId, fotografo.getNome(),
            ensaios.size(), totalValorCobrado, totalCustosFotografo,
            totalPartilha, totalRepasse, totalLucroCrm,
            ultimosEnsaios
        );
    }

    public List<FotografoEnsaiosResponse> listarEnsaios(UUID fotografoId) {
        return agendamentoFotografoRepository.findByFotografoIdWithAgendamento(fotografoId).stream()
            .map(l -> toEnsaiosResponse(l))
            .toList();
    }

    public BigDecimal calcularCustosFotografo(UUID agendamentoId, UUID fotografoId) {
        return despesaRepository.findByAgendamentoIdOrderByDataDesc(agendamentoId).stream()
            .filter(d -> fotografoId.equals(d.getFotografoId()))
            .map(Despesa::getValor)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public FotografoResumoFinanceiroResponse resumoFinanceiro(UUID fotografoId) {
        var fotografo = userRepository.findById(fotografoId)
            .orElseThrow(() -> new IllegalArgumentException("Fotógrafo não encontrado: " + fotografoId));
        var links = agendamentoFotografoRepository.findByFotografoIdWithAgendamento(fotografoId);
        var ensaios = links.stream().map(AgendamentoFotografo::getAgendamento).toList();

        var pendentes = (int) ensaios.stream().filter(a -> a.getStatus().name().equals("CONFIRMADO")).count();
        var realizados = (int) ensaios.stream().filter(a -> a.getStatus().name().equals("REALIZADO")).count();
        var finalizados = (int) ensaios.stream().filter(a -> a.getStatus().name().equals("FINALIZADO")).count();

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
            .map(l -> calcularCustosFotografo(l.getAgendamento().getId(), fotografoId))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        var mediaPartilha = ensaios.isEmpty() ? BigDecimal.ZERO
            : totalPartilha.divide(BigDecimal.valueOf(ensaios.size()), 2, RoundingMode.HALF_UP);

        var despesas = despesaRepository.findByFotografoIdOrderByDataDesc(fotografoId);
        var custosPorCategoria = new HashMap<String, BigDecimal>();
        for (var d : despesas) {
            var cat = d.getCategoria() != null ? d.getCategoria() : "Outros";
            custosPorCategoria.merge(cat, d.getValor(), BigDecimal::add);
        }

        var custosPorEnsaio = links.stream().map(l -> {
            var ag = l.getAgendamento();
            var custo = calcularCustosFotografo(ag.getId(), fotografoId);
            return new FotografoResumoFinanceiroResponse.CustoPorEnsaio(
                ag.getId(), ag.getCliente().getNome(),
                ag.getDataHoraEnsaio().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), custo
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

    public List<Despesa> listarCustos(UUID fotografoId) {
        return despesaRepository.findByFotografoIdOrderByDataDesc(fotografoId);
    }

    public FotografoRelatorioGlobalResponse relatorioGlobal() {
        var fotografos = userRepository.findByPapel(Papel.FOTOGRAFO);
        var items = new java.util.ArrayList<FotografoRelatorioGlobalResponse.FotografoItem>();

        var totalCustos = BigDecimal.ZERO;
        var totalRepasse = BigDecimal.ZERO;
        var ensaiosUnicos = new LinkedHashMap<UUID, Agendamento>();

        for (var f : fotografos) {
            var dashboard = dashboard(f.getId());
            items.add(new FotografoRelatorioGlobalResponse.FotografoItem(
                f.getNome(), dashboard.totalEnsaios(),
                dashboard.totalValorCobrado(), dashboard.totalCustosFotografo(),
                dashboard.totalPartilha(), dashboard.totalRepasse(),
                dashboard.totalLucroCrm()
            ));
            totalCustos = totalCustos.add(dashboard.totalCustosFotografo());
            totalRepasse = totalRepasse.add(dashboard.totalRepasse());
            for (var link : agendamentoFotografoRepository.findByFotografoIdWithAgendamento(f.getId())) {
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
        var custos = calcularCustosFotografo(a.getId(), fotografoId);
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