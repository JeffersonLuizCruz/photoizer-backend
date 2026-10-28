package com.photoizer.crm.agenda.service;

import com.photoizer.crm.agenda.api.AgendamentoMapper;
import com.photoizer.crm.agenda.api.AgendamentoResponse;
import com.photoizer.crm.agenda.api.AtualizarAgendamentoAvulsoRequest;
import com.photoizer.crm.agenda.api.OpcoesAgendamentoAvulsoResponse;
import com.photoizer.crm.agenda.exception.FotografoNaoEncontradoException;
import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.OrigemAgendamento;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.auth.model.Papel;
import com.photoizer.crm.auth.model.User;
import com.photoizer.crm.auth.repository.UserRepository;
import com.photoizer.crm.config.model.ConfigKey;
import com.photoizer.crm.config.service.ConfiguracaoService;
import com.photoizer.crm.financeiro.service.PagamentoManualCommand;
import com.photoizer.crm.financeiro.service.PagamentoService;
import com.photoizer.crm.pacote.exception.PacoteInativoException;
import com.photoizer.crm.pacote.service.PacoteQueryService;
import com.photoizer.crm.shared.exception.BadRequestException;
import com.photoizer.crm.shared.model.TipoPagamento;
import com.photoizer.crm.shared.util.EntradaUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AgendamentoAvulsoService {

    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    private final PacoteQueryService pacoteQueryService;
    private final UserRepository userRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final DisponibilidadeService disponibilidadeService;
    private final ConfiguracaoService configuracaoService;
    private final PagamentoService pagamentoService;
    private final AgendamentoMapper agendamentoMapper;
    private final AgendamentoService agendamentoService;

    public AgendamentoAvulsoService(PacoteQueryService pacoteQueryService,
                                    UserRepository userRepository,
                                    AgendamentoRepository agendamentoRepository,
                                    DisponibilidadeService disponibilidadeService,
                                    ConfiguracaoService configuracaoService,
                                    PagamentoService pagamentoService,
                                    AgendamentoMapper agendamentoMapper,
                                    AgendamentoService agendamentoService) {
        this.pacoteQueryService = pacoteQueryService;
        this.userRepository = userRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.disponibilidadeService = disponibilidadeService;
        this.configuracaoService = configuracaoService;
        this.pagamentoService = pagamentoService;
        this.agendamentoMapper = agendamentoMapper;
        this.agendamentoService = agendamentoService;
    }

    public Agendamento criar(CriarAgendamentoAvulsoCommand command) {
        var dataHoraEnsaio = command.dataHoraEnsaio();
        if (dataHoraEnsaio == null) {
            throw new BadRequestException("Informe a data e hora do ensaio.");
        }

        var agora = LocalDateTime.now();
        var ensaioNoPassado = dataHoraEnsaio.isBefore(agora);

        var pacote = pacoteQueryService.buscarEntityPorId(command.pacoteId());
        if (!pacote.getAtivo()) {
            throw new PacoteInativoException(pacote.getId());
        }

        var fotografo = userRepository.findById(command.fotografoId())
            .orElseThrow(() -> new FotografoNaoEncontradoException(command.fotografoId()));
        if (fotografo.getPapel() != Papel.FOTOGRAFO) {
            throw new BadRequestException("O responsável selecionado não é um fotógrafo.");
        }

        var taxaDeslocamentoPadrao = configuracaoService.getValorDecimal(ConfigKey.TAXA_DESLOCAMENTO);
        var custoDeslocamento = command.custoDeslocamento() != null
            ? command.custoDeslocamento()
            : taxaDeslocamentoPadrao;
        var repassarDeslocamento = command.repassarDeslocamento() != null
            ? command.repassarDeslocamento()
            : true;
        var taxaDeslocamento = repassarDeslocamento ? custoDeslocamento : BigDecimal.ZERO;

        var valorTotal = money(pacote.getValorBase().add(taxaDeslocamento));
        if (valorTotal.signum() <= 0) {
            throw new BadRequestException("O valor total deve ser maior que zero.");
        }

        var percentualConfigurado = configuracaoService.getValorDecimal(ConfigKey.PERCENTUAL_ENTRADA);
        var informouPercentual = command.percentualEntrada() != null;
        var informouValorRecebido = command.valorJaRecebido() != null;
        if (informouPercentual && informouValorRecebido) {
            throw new BadRequestException("Informe apenas o percentual de entrada ou o valor já recebido.");
        }

        BigDecimal percentualEntrada;
        BigDecimal valorEntradaExigido;
        BigDecimal valorPago;
        if (informouValorRecebido) {
            valorPago = money(command.valorJaRecebido());
            if (valorPago.signum() <= 0) {
                throw new BadRequestException("O valor já recebido deve ser maior que zero.");
            }
            if (valorPago.compareTo(valorTotal) > 0) {
                throw new BadRequestException("O valor já recebido não pode ser maior que o valor total.");
            }
            valorEntradaExigido = valorPago;
            percentualEntrada = valorPago.multiply(CEM).divide(valorTotal, 2, RoundingMode.HALF_UP);
        } else {
            percentualEntrada = money(informouPercentual ? command.percentualEntrada() : percentualConfigurado);
            if (percentualEntrada.signum() <= 0 || percentualEntrada.compareTo(CEM) > 0) {
                throw new BadRequestException("O percentual de entrada deve ser maior que zero e no máximo 100.");
            }
            valorEntradaExigido = EntradaUtils.calcularEntradaExigida(valorTotal, percentualEntrada);
            valorPago = BigDecimal.ZERO;
        }

        var duracao = command.duracaoMinutos() != null && command.duracaoMinutos() > 0
            ? command.duracaoMinutos()
            : 60;

        if (!ensaioNoPassado) {
            disponibilidadeService.validarConflitoAgenda(
                pacote, dataHoraEnsaio, duracao, command.localEnsaio(),
                ConflitoAgendaParams.paraCriacao(fotografo.getId()));
        }

        var valoresPersonalizados = informouValorRecebido
            || (informouPercentual && percentualEntrada.compareTo(percentualConfigurado) != 0);

        var agendamento = Agendamento.builder()
            .pacote(pacote)
            .fotografo(fotografo)
            .dataHoraEnsaio(dataHoraEnsaio)
            .duracaoMinutos(duracao)
            .localEnsaio(command.localEnsaio())
            .valorTotal(valorTotal)
            .valorEntradaExigido(valorEntradaExigido)
            .valorEntradaPago(valorPago)
            .valorRestante(valorTotal.subtract(valorPago).max(BigDecimal.ZERO))
            .valorExtras(BigDecimal.ZERO)
            .taxaDeslocamento(taxaDeslocamento)
            .custoDeslocamento(custoDeslocamento)
            .repassarDeslocamento(repassarDeslocamento)
            .valorTotalFinal(valorTotal)
            .percentualEntrada(percentualEntrada)
            .status(StatusAgendamento.CONFIRMADO)
            .origem(OrigemAgendamento.AVULSO)
            .valoresPersonalizados(valoresPersonalizados)
            .dataConfirmacao(agora)
            .autorizaUsoImagem(false)
            .ensaioDestaque(false)
            .observacoes(command.observacoes())
            .tokenGaleria(UUID.randomUUID())
            .tokenExpiracao(agora.plusDays(15))
            .build();

        PropostaTokenUtils.gerar(agendamento,
            configuracaoService.getValorInteiro(ConfigKey.CONTRATO_DIAS_VALIDADE));
        agendamento.registrarEnvioProposta();

        agendamento = agendamentoRepository.save(agendamento);

        if (valorPago.signum() > 0) {
            var dataRecebimento = command.dataRecebimento() != null ? command.dataRecebimento() : agora;
            pagamentoService.registrarPagamentosHistoricos(agendamento.getId(), List.of(
                new PagamentoManualCommand(
                    valorPago,
                    dataRecebimento,
                    command.formaPagamento(),
                    TipoPagamento.ENTRADA,
                    "Reserva recebida (agendamento avulso)"
                )
            ));
        }

        return agendamento;
    }

    public AgendamentoResponse atualizar(UUID id, AtualizarAgendamentoAvulsoRequest request) {
        return agendamentoService.atualizarAvulso(id, request);
    }

    @Transactional(readOnly = true)
    public OpcoesAgendamentoAvulsoResponse opcoes() {
        var fotografos = userRepository.findByPapel(Papel.FOTOGRAFO).stream()
            .filter(User::isAtivo)
            .map(u -> new OpcoesAgendamentoAvulsoResponse.ProfissionalOpcao(u.getId(), u.getNome()))
            .toList();
        return new OpcoesAgendamentoAvulsoResponse(
            fotografos,
            configuracaoService.getValorDecimal(ConfigKey.PERCENTUAL_ENTRADA),
            configuracaoService.getValorDecimal(ConfigKey.TAXA_DESLOCAMENTO)
        );
    }

    public AgendamentoResponse toResponse(Agendamento agendamento) {
        return agendamentoMapper.toResponse(agendamento, null, null, null, null);
    }

    private BigDecimal money(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }
}
