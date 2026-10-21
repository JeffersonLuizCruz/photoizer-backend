package com.photoizer.crm.agenda.service;

import com.photoizer.crm.agenda.api.AgendamentoMapper;
import com.photoizer.crm.agenda.api.OpcoesAgendamentoAvulsoResponse;
import com.photoizer.crm.agenda.exception.AgendamentoNoPassadoException;
import com.photoizer.crm.agenda.exception.FotografoNaoEncontradoException;
import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.OrigemAgendamento;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.auth.model.Papel;
import com.photoizer.crm.auth.model.User;
import com.photoizer.crm.auth.repository.UserRepository;
import com.photoizer.crm.cliente.model.Cliente;
import com.photoizer.crm.cliente.model.OrigemCliente;
import com.photoizer.crm.cliente.repository.ClienteRepository;
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

    private final ClienteRepository clienteRepository;
    private final PacoteQueryService pacoteQueryService;
    private final UserRepository userRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final DisponibilidadeService disponibilidadeService;
    private final ConfiguracaoService configuracaoService;
    private final PagamentoService pagamentoService;
    private final AgendamentoMapper agendamentoMapper;

    public AgendamentoAvulsoService(ClienteRepository clienteRepository,
                                    PacoteQueryService pacoteQueryService,
                                    UserRepository userRepository,
                                    AgendamentoRepository agendamentoRepository,
                                    DisponibilidadeService disponibilidadeService,
                                    ConfiguracaoService configuracaoService,
                                    PagamentoService pagamentoService,
                                    AgendamentoMapper agendamentoMapper) {
        this.clienteRepository = clienteRepository;
        this.pacoteQueryService = pacoteQueryService;
        this.userRepository = userRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.disponibilidadeService = disponibilidadeService;
        this.configuracaoService = configuracaoService;
        this.pagamentoService = pagamentoService;
        this.agendamentoMapper = agendamentoMapper;
    }

    public Agendamento criar(CriarAgendamentoAvulsoCommand command) {
        var dataHoraEnsaio = command.dataHoraEnsaio();
        if (dataHoraEnsaio == null || dataHoraEnsaio.isBefore(LocalDateTime.now())) {
            throw new AgendamentoNoPassadoException();
        }

        var pacote = pacoteQueryService.buscarEntityPorId(command.pacoteId());
        if (!pacote.getAtivo()) {
            throw new PacoteInativoException(pacote.getId());
        }

        var fotografo = userRepository.findById(command.fotografoId())
            .orElseThrow(() -> new FotografoNaoEncontradoException(command.fotografoId()));
        if (fotografo.getPapel() != Papel.FOTOGRAFO) {
            throw new BadRequestException("O responsável selecionado não é um fotógrafo.");
        }

        var cliente = resolverCliente(command);

        var taxaDeslocamentoPadrao = configuracaoService.getValorDecimal(ConfigKey.TAXA_DESLOCAMENTO);
        var custoDeslocamento = command.custoDeslocamento() != null
            ? command.custoDeslocamento()
            : taxaDeslocamentoPadrao;
        var repassarDeslocamento = command.repassarDeslocamento() != null
            ? command.repassarDeslocamento()
            : true;
        var taxaDeslocamento = repassarDeslocamento ? custoDeslocamento : BigDecimal.ZERO;

        var percentualEntradaConfig = configuracaoService.getValorDecimal(ConfigKey.PERCENTUAL_ENTRADA);
        var valorTotalPadrao = pacote.getValorBase().add(taxaDeslocamento);
        var valorTotal = money(command.valorTotal() != null ? command.valorTotal() : valorTotalPadrao);
        var valorEntradaPadrao = EntradaUtils.calcularEntradaExigida(valorTotal, percentualEntradaConfig);
        var valorEntradaExigido = money(command.valorEntradaExigido() != null
            ? command.valorEntradaExigido()
            : valorEntradaPadrao);

        if (valorTotal.signum() <= 0) {
            throw new BadRequestException("O valor total deve ser maior que zero.");
        }
        if (valorEntradaExigido.signum() <= 0) {
            throw new BadRequestException("A entrada exigida deve ser maior que zero.");
        }
        if (valorEntradaExigido.compareTo(valorTotal) > 0) {
            throw new BadRequestException("A entrada exigida não pode ser maior que o valor total.");
        }

        var valorPago = money(command.valorPago() != null ? command.valorPago() : BigDecimal.ZERO);
        if (valorPago.compareTo(valorTotal) > 0) {
            throw new BadRequestException("O valor pago não pode ser maior que o valor total.");
        }

        var duracao = command.duracaoMinutos() != null && command.duracaoMinutos() > 0
            ? command.duracaoMinutos()
            : 60;

        disponibilidadeService.validarConflitoAgenda(
            pacote, dataHoraEnsaio, duracao, command.localEnsaio(),
            ConflitoAgendaParams.paraCriacao(fotografo.getId()));

        var percentualEntrada = valorTotal.signum() > 0
            ? valorEntradaExigido.multiply(CEM).divide(valorTotal, 2, RoundingMode.HALF_UP)
            : percentualEntradaConfig;
        var valoresPersonalizados = valorTotal.compareTo(valorTotalPadrao) != 0
            || valorEntradaExigido.compareTo(valorEntradaPadrao) != 0;

        var tipoPagamento = command.tipoPagamento() != null
            ? command.tipoPagamento()
            : (valorPago.compareTo(valorTotal) >= 0 ? TipoPagamento.FINAL : TipoPagamento.ENTRADA);
        var registraPagamento = valorPago.signum() > 0;
        var dataPagamento = command.dataPagamento() != null ? command.dataPagamento() : LocalDateTime.now();

        var agendamento = Agendamento.builder()
            .cliente(cliente)
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
            .dataConfirmacao(LocalDateTime.now())
            .dataPagamentoFinal(registraPagamento && tipoPagamento == TipoPagamento.FINAL ? dataPagamento : null)
            .formaPagamentoFinal(registraPagamento && tipoPagamento == TipoPagamento.FINAL ? command.formaPagamento() : null)
            .autorizaUsoImagem(Boolean.TRUE.equals(command.autorizaUsoImagem()))
            .ensaioDestaque(false)
            .observacoes(command.observacoes())
            .tokenGaleria(UUID.randomUUID())
            .tokenExpiracao(LocalDateTime.now().plusDays(15))
            .build();

        agendamento = agendamentoRepository.save(agendamento);

        if (registraPagamento) {
            pagamentoService.registrarPagamentosHistoricos(agendamento.getId(), List.of(
                new PagamentoManualCommand(
                    valorPago,
                    dataPagamento,
                    command.formaPagamento(),
                    tipoPagamento,
                    "Recebimento inicial (agendamento avulso)"
                )
            ));
        }

        return agendamento;
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

    public com.photoizer.crm.agenda.api.AgendamentoResponse toResponse(Agendamento agendamento) {
        return agendamentoMapper.toResponse(agendamento, null, null, null, null);
    }

    private Cliente resolverCliente(CriarAgendamentoAvulsoCommand command) {
        var telefone = normalizarTelefone(command.clienteTelefone());
        var digitosTelefone = command.clienteTelefone().replaceAll("\\D", "");
        var cpf = normalizarCpf(command.clienteCpf());

        var porTelefone = clienteRepository.findByTelefone(telefone)
            .or(() -> clienteRepository.findByTelefoneContaining(digitosTelefone).stream()
                .filter(c -> c.getTelefone() != null
                    && c.getTelefone().replaceAll("\\D", "").equals(digitosTelefone))
                .findFirst());
        var porCpf = cpf != null ? clienteRepository.findByCpf(cpf) : java.util.Optional.<Cliente>empty();

        if (porTelefone.isPresent() && porCpf.isPresent()
            && !porTelefone.get().getId().equals(porCpf.get().getId())) {
            throw new BadRequestException("Telefone e CPF informados pertencem a clientes diferentes.");
        }

        if (porTelefone.isPresent()) {
            return porTelefone.get();
        }
        if (porCpf.isPresent()) {
            return porCpf.get();
        }

        var cliente = Cliente.builder()
            .nome(command.clienteNome().trim())
            .telefone(telefone)
            .email(command.clienteEmail())
            .cpf(cpf)
            .cidade(command.clienteCidade())
            .estado(command.clienteEstado() != null ? command.clienteEstado().toUpperCase() : null)
            .origem(OrigemCliente.OUTROS)
            .build();
        return clienteRepository.save(cliente);
    }

    private String normalizarTelefone(String telefone) {
        var digitos = telefone != null ? telefone.replaceAll("\\D", "") : "";
        if (digitos.length() == 11) {
            return "(" + digitos.substring(0, 2) + ") "
                + digitos.substring(2, 7) + "-" + digitos.substring(7);
        }
        if (digitos.length() == 10) {
            return "(" + digitos.substring(0, 2) + ") "
                + digitos.substring(2, 6) + "-" + digitos.substring(6);
        }
        throw new BadRequestException("Telefone inválido: informe DDD e número.");
    }

    private String normalizarCpf(String cpf) {
        if (cpf == null || cpf.isBlank()) {
            return null;
        }
        var digitos = cpf.replaceAll("\\D", "");
        if (digitos.length() != 11) {
            throw new BadRequestException("CPF inválido: informe 11 dígitos.");
        }
        return digitos.replaceAll("(\\d{3})(\\d{3})(\\d{3})(\\d{2})", "$1.$2.$3-$4");
    }

    private BigDecimal money(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }
}
