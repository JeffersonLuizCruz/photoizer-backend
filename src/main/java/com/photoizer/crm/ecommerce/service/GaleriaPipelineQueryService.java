package com.photoizer.crm.ecommerce.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.ecommerce.api.GaleriaPipelineItemResponse;
import com.photoizer.crm.ecommerce.api.GaleriaPipelineResumoResponse;
import com.photoizer.crm.ecommerce.api.GaleriaPerformanceResponse;
import com.photoizer.crm.ecommerce.api.GaleriaStatusResponse;
import com.photoizer.crm.ecommerce.model.EtapaGaleria;
import com.photoizer.crm.ecommerce.model.GaleriaEnsaio;
import com.photoizer.crm.ecommerce.model.SelecaoEnsaio;
import com.photoizer.crm.ecommerce.model.StatusCompraExtra;
import com.photoizer.crm.ecommerce.model.StatusSelecao;
import com.photoizer.crm.ecommerce.repository.CompraExtraRepository;
import com.photoizer.crm.ecommerce.repository.SelecaoEnsaioRepository;
import com.photoizer.crm.ecommerce.service.GaleriaPerformanceCalculator.DadosGaleriaPerformance;
import com.photoizer.crm.ecommerce.service.GaleriaPipelineEtapaResolver.DadosEtapa;
import com.photoizer.crm.foto.repository.FotoEnsaioRepository;
import com.photoizer.crm.shared.api.PageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Query service da Central de Galerias.
 *
 * <p>Le o estado que ja existe em agenda, foto, selecao e compras e deriva uma
 * etapa operacional por galeria. Nada e persistido: a fila sempre reflete o
 * fluxo real. As consultas usam agregados por agendamento para nao carregar
 * todas as fotos em memoria.
 */
@Service
@Transactional(readOnly = true)
public class GaleriaPipelineQueryService {

    private static final int JANELA_MESES = 12;

    private static final List<StatusAgendamento> STATUS_PIPELINE = List.of(
        StatusAgendamento.EM_EDICAO,
        StatusAgendamento.FINALIZADO);

    private static final List<StatusAgendamento> STATUS_DESEMPENHO = List.of(
        StatusAgendamento.REALIZADO,
        StatusAgendamento.AGUARDANDO_PAGAMENTO_FINAL,
        StatusAgendamento.EM_EDICAO,
        StatusAgendamento.FINALIZADO);

    private static final int MAX_MESES_DESEMPENHO = 24;
    private static final DateTimeFormatter DATA_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final List<StatusSelecao> STATUS_SELECAO_RELEVANTES = List.of(
        StatusSelecao.ENVIADA,
        StatusSelecao.EM_EDICAO,
        StatusSelecao.CONCLUIDA,
        StatusSelecao.PUBLICADA);

    private static final List<StatusCompraExtra> STATUS_COMPRA_PENDENTE = List.of(
        StatusCompraExtra.AGUARDANDO_COMPROVANTE,
        StatusCompraExtra.AGUARDANDO_CONFIRMACAO);

    private final AgendamentoRepository agendamentoRepository;
    private final FotoEnsaioRepository fotoEnsaioRepository;
    private final SelecaoEnsaioRepository selecaoEnsaioRepository;
    private final CompraExtraRepository compraExtraRepository;
    private final GaleriaEnsaioService galeriaEnsaioService;
    private final GaleriaQueryService galeriaQueryService;

    public GaleriaPipelineQueryService(AgendamentoRepository agendamentoRepository,
                                       FotoEnsaioRepository fotoEnsaioRepository,
                                       SelecaoEnsaioRepository selecaoEnsaioRepository,
                                       CompraExtraRepository compraExtraRepository,
                                       GaleriaEnsaioService galeriaEnsaioService,
                                       GaleriaQueryService galeriaQueryService) {
        this.agendamentoRepository = agendamentoRepository;
        this.fotoEnsaioRepository = fotoEnsaioRepository;
        this.selecaoEnsaioRepository = selecaoEnsaioRepository;
        this.compraExtraRepository = compraExtraRepository;
        this.galeriaEnsaioService = galeriaEnsaioService;
        this.galeriaQueryService = galeriaQueryService;
    }

    public PageResponse<GaleriaPipelineItemResponse> listar(
            EtapaGaleria etapa, String busca, boolean atrasadas, int page, int perPage) {
        var filtrados = listarTodos(etapa, busca, atrasadas);

        var pagina = Math.max(1, page);
        var tamanho = Math.min(Math.max(1, perPage), 100);
        var inicio = Math.min((pagina - 1) * tamanho, filtrados.size());
        var fim = Math.min(inicio + tamanho, filtrados.size());
        var totalPaginas = (int) Math.ceil((double) filtrados.size() / tamanho);

        return new PageResponse<>(
            List.copyOf(filtrados.subList(inicio, fim)),
            filtrados.size(),
            pagina,
            tamanho,
            totalPaginas);
    }

    public List<GaleriaPipelineItemResponse> listarTodos(
            EtapaGaleria etapa, String busca, boolean atrasadas) {
        return carregarItens(busca).stream()
            .filter(item -> etapa == null || item.etapa() == etapa)
            .filter(item -> !atrasadas || item.atrasada())
            .toList();
    }

    public GaleriaPipelineResumoResponse resumo() {
        var itens = carregarItens(null);
        return new GaleriaPipelineResumoResponse(
            itens.size(),
            contar(itens, EtapaGaleria.AGUARDANDO_UPLOAD),
            contar(itens, EtapaGaleria.FOTOS_NAO_PUBLICADAS),
            contar(itens, EtapaGaleria.AGUARDANDO_SELECAO),
            contar(itens, EtapaGaleria.SELECAO_EM_EDICAO),
            contar(itens, EtapaGaleria.SELECAO_AGUARDANDO_APROVACAO),
            contar(itens, EtapaGaleria.AGUARDANDO_DOWNLOAD),
            contar(itens, EtapaGaleria.CONCLUIDA),
            contar(itens, EtapaGaleria.ARQUIVADA),
            (int) itens.stream().filter(GaleriaPipelineItemResponse::atrasada).count(),
            itens.stream().mapToInt(GaleriaPipelineItemResponse::comprasPendentes).sum(),
            itens.stream().mapToInt(GaleriaPipelineItemResponse::extrasPendentesEdicao).sum());
    }

    /**
     * Status publico da galeria. Funciona antes da publicacao das fotos, para o
     * cliente entender em que ponto o ensaio esta.
     */
    public GaleriaStatusResponse statusPublico(UUID token) {
        var agendamento = galeriaQueryService.buscarAgendamentoPorToken(token);
        var item = montarItens(List.of(agendamento)).getFirst();
        return new GaleriaStatusResponse(
            item.etapa(),
            item.etapaLabel(),
            mensagemCliente(item.etapa()),
            item.prazoEntrega(),
            item.totalFotos(),
            item.fotosPublicadas(),
            item.selecaoStatus(),
            item.selecaoEnviadaEm(),
            item.tokenExpiracao());
    }

    /**
     * Indicadores de gestao do periodo: SLA de publicacao/edicao, funil,
     * extras e serie mensal. Usa os mesmos agregados da fila.
     */
    public GaleriaPerformanceResponse desempenho(int meses) {
        var periodo = Math.min(Math.max(meses, 1), MAX_MESES_DESEMPENHO);
        var inicio = LocalDate.now().minusMonths(periodo - 1L).withDayOfMonth(1).atStartOfDay();
        var agendamentos = agendamentoRepository.findParaDesempenho(STATUS_DESEMPENHO, inicio);
        var contexto = carregarContexto(agendamentos);
        var hoje = LocalDate.now();
        var dados = new ArrayList<DadosGaleriaPerformance>(agendamentos.size());

        for (var agendamento : agendamentos) {
            var galeria = contexto.galerias().get(agendamento.getId());
            if (galeria == null) {
                continue;
            }
            var fotos = contexto.fotos().get(agendamento.getId());
            var compras = contexto.compras().get(agendamento.getId());
            var selecao = contexto.selecoes().get(agendamento.getId());

            var totalFotos = fotos != null ? (int) fotos.getTotal() : 0;
            var publicadas = fotos != null ? (int) fotos.getPublicadas() : 0;
            var arquivadas = fotos != null ? fotos.getArquivadas() : 0;
            var etapa = GaleriaPipelineEtapaResolver.resolver(new DadosEtapa(
                agendamento.getStatus(),
                galeria.getModo(),
                galeria.getFase(),
                totalFotos,
                publicadas,
                arquivadas,
                selecao != null ? selecao.getStatus() : null));
            var prazoEntrega = calcularPrazo(agendamento);
            var dataEnsaio = agendamento.getDataRealizacao() != null
                ? agendamento.getDataRealizacao()
                : agendamento.getDataHoraEnsaio();
            var comercializada = galeria.getFase().permiteComercializar()
                || (selecao != null && selecao.getStatus() == StatusSelecao.PUBLICADA);
            var comprasPagas = compras != null ? (int) compras.getPagas() : 0;
            var fotosPagas = fotos != null ? (int) fotos.getPagas() : 0;
            var receita = compras != null && compras.getValorPago() != null
                ? compras.getValorPago()
                : BigDecimal.ZERO;

            dados.add(new DadosGaleriaPerformance(
                dataEnsaio,
                publicadas > 0,
                comercializada,
                prazoEntrega != null && hoje.isAfter(prazoEntrega) && !etapa.isTerminal(),
                fotosPagas,
                comprasPagas,
                receita,
                galeria.getPublicadaEm(),
                selecao != null ? selecao.getEnviadaEm() : null,
                selecao != null ? selecao.getPublicadaEm() : null));
        }

        return GaleriaPerformanceCalculator.calcular(LocalDate.now(), periodo, dados);
    }

    /**
     * Exporta a fila filtrada em CSV (separador ';' e BOM no controller), para
     * follow-up fora do sistema. Nao altera nenhum dado.
     */
    public String gerarCsv(EtapaGaleria etapa, String busca, boolean atrasadas) {
        var itens = listarTodos(etapa, busca, atrasadas);
        var csv = new StringBuilder();
        csv.append("Cliente;Pacote;Data do ensaio;Etapa;Prazo de entrega;Dias na etapa;")
            .append("Atrasada;Link expirado;Total de fotos;Publicadas;Selecionadas;Baixadas;")
            .append("Extras a editar;Pagamentos pendentes;Valor extras;Proxima acao\n");
        for (var item : itens) {
            csv.append(valorCsv(item.clienteNome())).append(';')
                .append(valorCsv(item.pacoteNome())).append(';')
                .append(valorCsv(formatarData(item.dataHoraEnsaio()))).append(';')
                .append(valorCsv(item.etapaLabel())).append(';')
                .append(valorCsv(item.prazoEntrega() != null
                    ? item.prazoEntrega().format(DATA_FORMATTER)
                    : "")).append(';')
                .append(item.diasNaEtapa()).append(';')
                .append(item.atrasada() ? "Sim" : "Nao").append(';')
                .append(item.tokenExpirado() ? "Sim" : "Nao").append(';')
                .append(item.totalFotos()).append(';')
                .append(item.fotosPublicadas()).append(';')
                .append(item.fotosSelecionadas()).append(';')
                .append(item.fotosBaixadas()).append(';')
                .append(item.extrasPendentesEdicao()).append(';')
                .append(item.comprasPendentes()).append(';')
                .append(item.valorExtras() != null ? item.valorExtras().toPlainString() : "0").append(';')
                .append(valorCsv(item.proximaAcao())).append('\n');
        }
        return csv.toString();
    }

    private List<GaleriaPipelineItemResponse> carregarItens(String busca) {
        var desde = LocalDate.now().minusMonths(JANELA_MESES).atStartOfDay();
        var agendamentos = agendamentoRepository.findParaPipeline(STATUS_PIPELINE, desde);
        if (agendamentos.isEmpty()) {
            return List.of();
        }
        var termo = normalizar(busca);
        if (termo != null) {
            agendamentos = agendamentos.stream()
                .filter(agendamento -> correspondeBusca(agendamento, termo))
                .toList();
        }
        return montarItens(agendamentos);
    }

    private List<GaleriaPipelineItemResponse> montarItens(List<Agendamento> agendamentos) {
        return montarItens(carregarContexto(agendamentos));
    }

    private ContextoPipeline carregarContexto(List<Agendamento> agendamentos) {
        if (agendamentos.isEmpty()) {
            return new ContextoPipeline(List.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }

        var ids = agendamentos.stream().map(Agendamento::getId).toList();
        var fotos = fotoEnsaioRepository.agregarPipeline(ids).stream()
            .collect(Collectors.toMap(
                FotoEnsaioRepository.GaleriaPipelineFotos::getAgendamentoId,
                Function.identity()));
        var compras = compraExtraRepository.agregarPipeline(ids, STATUS_COMPRA_PENDENTE).stream()
            .collect(Collectors.toMap(
                CompraExtraRepository.CompraPipelineAgregado::getAgendamentoId,
                Function.identity()));
        var selecoes = new HashMap<UUID, SelecaoEnsaio>();
        selecaoEnsaioRepository
            .findByAgendamentoIdInAndStatusInOrderByNumeroDesc(ids, STATUS_SELECAO_RELEVANTES)
            .forEach(selecao -> selecoes.putIfAbsent(selecao.getAgendamentoId(), selecao));
        var galerias = galeriaEnsaioService.obterEmLote(ids);
        return new ContextoPipeline(agendamentos, fotos, compras, selecoes, galerias);
    }

    private List<GaleriaPipelineItemResponse> montarItens(ContextoPipeline contexto) {
        if (contexto.agendamentos().isEmpty()) {
            return List.of();
        }
        var agendamentos = contexto.agendamentos();
        var fotos = contexto.fotos();
        var compras = contexto.compras();
        var selecoes = contexto.selecoes();
        var galerias = contexto.galerias();

        var hoje = LocalDate.now();
        var agora = LocalDateTime.now();
        var itens = new ArrayList<GaleriaPipelineItemResponse>(agendamentos.size());

        for (var agendamento : agendamentos) {
            var galeria = galerias.get(agendamento.getId());
            if (galeria == null) {
                continue;
            }
            var agregadoFotos = fotos.get(agendamento.getId());
            var agregadoCompras = compras.get(agendamento.getId());
            var selecao = selecoes.get(agendamento.getId());

            var totalFotos = agregadoFotos != null ? (int) agregadoFotos.getTotal() : 0;
            var fotosPublicadas = agregadoFotos != null ? (int) agregadoFotos.getPublicadas() : 0;
            var arquivadas = agregadoFotos != null ? agregadoFotos.getArquivadas() : 0;
            var selecionadas = agregadoFotos != null ? (int) agregadoFotos.getSelecionadas() : 0;
            var baixadas = agregadoFotos != null ? (int) agregadoFotos.getBaixadas() : 0;
            var extrasPendentes = agregadoFotos != null ? (int) agregadoFotos.getExtrasPendentesEdicao() : 0;
            var comprasPendentes = agregadoCompras != null ? (int) agregadoCompras.getPendentes() : 0;
            var valorExtras = agregadoCompras != null && agregadoCompras.getValorPago() != null
                ? agregadoCompras.getValorPago()
                : BigDecimal.ZERO;

            var etapa = GaleriaPipelineEtapaResolver.resolver(new DadosEtapa(
                agendamento.getStatus(),
                galeria.getModo(),
                galeria.getFase(),
                totalFotos,
                fotosPublicadas,
                arquivadas,
                selecao != null ? selecao.getStatus() : null));
            var acao = resolverAcao(agendamento.getId(), etapa, selecao != null ? selecao.getStatus() : null);
            var prazoEntrega = calcularPrazo(agendamento);
            var tokenExpirado = agendamento.getTokenExpiracao() != null
                && agendamento.getTokenExpiracao().isBefore(agora);
            var referencia = referenciaEtapa(etapa, agendamento, galeria, selecao);
            var diasNaEtapa = referencia == null
                ? 0
                : Math.max(0, ChronoUnit.DAYS.between(referencia.toLocalDate(), hoje));

            itens.add(new GaleriaPipelineItemResponse(
                agendamento.getId(),
                agendamento.nomeCliente(),
                agendamento.getPacote() != null ? agendamento.getPacote().getNome() : null,
                agendamento.getLocalEnsaio(),
                agendamento.getDataHoraEnsaio(),
                prazoEntrega,
                etapa,
                etapa.getLabel(),
                acao.label(),
                acao.url(),
                agendamento.getTokenGaleria() != null ? "/g/" + agendamento.getTokenGaleria() : null,
                totalFotos,
                fotosPublicadas,
                selecionadas,
                baixadas,
                extrasPendentes,
                comprasPendentes,
                valorExtras,
                selecao != null ? selecao.getStatus().name() : null,
                selecao != null ? selecao.getNumero() : null,
                selecao != null ? selecao.getEnviadaEm() : null,
                galeria.getPublicadaEm(),
                diasNaEtapa,
                prazoEntrega != null && hoje.isAfter(prazoEntrega) && !etapa.isTerminal(),
                tokenExpirado,
                agendamento.getTokenExpiracao()));
        }

        return itens.stream()
            .sorted(Comparator
                .comparing((GaleriaPipelineItemResponse item) -> !item.atrasada())
                .thenComparingInt(item -> item.etapa().getPrioridade())
                .thenComparing(item -> item.dataHoraEnsaio() != null
                    ? item.dataHoraEnsaio()
                    : LocalDateTime.MAX))
            .toList();
    }

    private LocalDateTime referenciaEtapa(
            EtapaGaleria etapa, Agendamento agendamento,
            GaleriaEnsaio galeria, SelecaoEnsaio selecao) {
        var realizado = agendamento.getDataRealizacao() != null
            ? agendamento.getDataRealizacao()
            : agendamento.getDataHoraEnsaio();
        return switch (etapa) {
            case AGUARDANDO_UPLOAD, FOTOS_NAO_PUBLICADAS -> realizado;
            case AGUARDANDO_SELECAO -> galeria.getPublicadaEm() != null
                ? galeria.getPublicadaEm()
                : realizado;
            case SELECAO_EM_EDICAO, SELECAO_AGUARDANDO_APROVACAO -> selecao != null
                ? selecao.getEnviadaEm()
                : null;
            case AGUARDANDO_DOWNLOAD -> selecao != null && selecao.getPublicadaEm() != null
                ? selecao.getPublicadaEm()
                : galeria.getPublicadaEm();
            case CONCLUIDA, ARQUIVADA -> null;
        };
    }

    private LocalDate calcularPrazo(Agendamento agendamento) {
        var pacote = agendamento.getPacote();
        if (pacote == null || pacote.getDiasParaEntrega() == null || pacote.getDiasParaEntrega() <= 0) {
            return null;
        }
        var base = agendamento.getDataRealizacao() != null
            ? agendamento.getDataRealizacao().toLocalDate()
            : agendamento.getDataHoraEnsaio().toLocalDate();
        return base.plusDays(pacote.getDiasParaEntrega());
    }

    private Acao resolverAcao(UUID agendamentoId, EtapaGaleria etapa, StatusSelecao selecaoStatus) {
        var galeriaUrl = "/agenda/" + agendamentoId + "/fotos";
        return switch (etapa) {
            case AGUARDANDO_UPLOAD -> new Acao("Enviar fotos", galeriaUrl + "?tab=fotos");
            case FOTOS_NAO_PUBLICADAS -> new Acao("Publicar fotos", galeriaUrl + "?tab=fotos");
            case SELECAO_EM_EDICAO -> new Acao(rotuloEdicao(selecaoStatus), galeriaUrl + "?tab=selecao");
            case SELECAO_AGUARDANDO_APROVACAO -> new Acao("Aprovar seleção", galeriaUrl + "?tab=selecao");
            case AGUARDANDO_SELECAO -> new Acao("Acompanhar seleção", galeriaUrl + "?tab=visao");
            case AGUARDANDO_DOWNLOAD -> new Acao("Acompanhar entrega", galeriaUrl + "?tab=visao");
            case CONCLUIDA -> new Acao("Ver galeria", galeriaUrl + "?tab=visao");
            case ARQUIVADA -> new Acao("Ver armazenamento", "/admin/armazenamento");
        };
    }

    private String rotuloEdicao(StatusSelecao status) {
        if (status == StatusSelecao.ENVIADA) {
            return "Iniciar edição";
        }
        if (status == StatusSelecao.CONCLUIDA) {
            return "Publicar edição";
        }
        return "Continuar edição";
    }

    private String mensagemCliente(EtapaGaleria etapa) {
        return switch (etapa) {
            case AGUARDANDO_UPLOAD -> "Estamos organizando as fotos do seu ensaio.";
            case FOTOS_NAO_PUBLICADAS ->
                "As fotos do seu ensaio estão em revisão. Em breve a galeria será liberada.";
            case AGUARDANDO_SELECAO ->
                "Sua galeria está pronta. Escolha as fotos do seu pacote.";
            case SELECAO_EM_EDICAO ->
                "Recebemos sua seleção e estamos preparando as versões finais das fotos.";
            case SELECAO_AGUARDANDO_APROVACAO ->
                "Recebemos sua seleção. Estamos na revisão final para liberar o seu pacote.";
            case AGUARDANDO_DOWNLOAD ->
                "Sua galeria está liberada. Você já pode baixar as fotos do pacote e comprar extras.";
            case CONCLUIDA ->
                "Seu ensaio foi entregue. A galeria continua disponível para consulta.";
            case ARQUIVADA ->
                "Esta galeria foi arquivada.";
        };
    }

    private int contar(List<GaleriaPipelineItemResponse> itens, EtapaGaleria etapa) {
        return (int) itens.stream().filter(item -> item.etapa() == etapa).count();
    }

    private boolean correspondeBusca(Agendamento agendamento, String termo) {
        return contem(agendamento.nomeCliente(), termo)
            || contem(agendamento.getPacote() != null ? agendamento.getPacote().getNome() : null, termo)
            || contem(agendamento.getLocalEnsaio(), termo);
    }

    private boolean contem(String valor, String termo) {
        var normalizado = normalizar(valor);
        return normalizado != null && normalizado.contains(termo);
    }

    private String normalizar(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return Normalizer.normalize(valor.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
    }

    private String formatarData(LocalDateTime valor) {
        return valor == null ? "" : valor.format(DATA_FORMATTER);
    }

    private String valorCsv(String valor) {
        if (valor == null) {
            return "";
        }
        return "\"" + valor.replace("\"", "\"\"") + "\"";
    }

    private record ContextoPipeline(
        List<Agendamento> agendamentos,
        Map<UUID, FotoEnsaioRepository.GaleriaPipelineFotos> fotos,
        Map<UUID, CompraExtraRepository.CompraPipelineAgregado> compras,
        Map<UUID, SelecaoEnsaio> selecoes,
        Map<UUID, GaleriaEnsaio> galerias
    ) {
    }

    private record Acao(String label, String url) {
    }
}
