package com.photoizer.crm.financeiro.service;

import com.photoizer.crm.cliente.repository.ClienteRepository;
import com.photoizer.crm.config.model.ConfigKey;
import com.photoizer.crm.config.service.ConfiguracaoService;
import com.photoizer.crm.financeiro.api.ReceitaRequest;
import com.photoizer.crm.financeiro.exception.ClienteObrigatorioException;
import com.photoizer.crm.financeiro.exception.ReceitaNaoEncontradaException;
import com.photoizer.crm.financeiro.exception.ValorRecebidoExcedeFinalException;
import com.photoizer.crm.financeiro.model.Receita;
import com.photoizer.crm.financeiro.model.StatusReceita;
import com.photoizer.crm.financeiro.model.TipoServico;
import com.photoizer.crm.financeiro.repository.ReceitaRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ReceitaService {

    private final ReceitaRepository receitaRepository;
    private final ClienteRepository clienteRepository;
    private final ConfiguracaoService configuracaoService;

    public ReceitaService(ReceitaRepository receitaRepository,
                          ClienteRepository clienteRepository,
                          ConfiguracaoService configuracaoService) {
        this.receitaRepository = receitaRepository;
        this.clienteRepository = clienteRepository;
        this.configuracaoService = configuracaoService;
    }

    public Receita criar(ReceitaRequest request) {
        var receita = Receita.builder()
            .agendamentoId(null)
            .tipoServico(request.tipoServico() != null ? request.tipoServico() : TipoServico.ENSAIO)
            .descricao(request.descricao())
            .valorBruto(request.valorBruto())
            .valorRecebido(request.valorRecebido() != null ? request.valorRecebido() : BigDecimal.ZERO)
            .dataPrevisaoRecebimento(request.dataPrevisaoRecebimento())
            .dataRecebimentoReal(request.dataRecebimentoReal())
            .formaPagamento(request.formaPagamento())
            .observacoes(request.observacoes())
            .build();

        preencherCliente(receita, request);
        preencherComissao(receita);
        preencherStatus(receita, request.status());

        return receitaRepository.save(receita);
    }

    public Receita atualizar(UUID id, ReceitaRequest request) {
        var receita = receitaRepository.findById(id)
            .orElseThrow(() -> new ReceitaNaoEncontradaException(id));

        receita.setAgendamentoId(null);
        receita.setTipoServico(request.tipoServico() != null ? request.tipoServico() : TipoServico.ENSAIO);
        receita.setDescricao(request.descricao());
        receita.setValorBruto(request.valorBruto());
        receita.setValorRecebido(request.valorRecebido() != null ? request.valorRecebido() : BigDecimal.ZERO);
        receita.setDataPrevisaoRecebimento(request.dataPrevisaoRecebimento());
        receita.setDataRecebimentoReal(request.dataRecebimentoReal());
        receita.setFormaPagamento(request.formaPagamento());
        receita.setObservacoes(request.observacoes());

        preencherCliente(receita, request);
        preencherComissao(receita);
        preencherStatus(receita, request.status());

        return receitaRepository.save(receita);
    }

    public void excluir(UUID id) {
        if (!receitaRepository.existsById(id)) {
            throw new ReceitaNaoEncontradaException(id);
        }
        receitaRepository.deleteById(id);
    }

    public Receita receber(UUID id) {
        var receita = receitaRepository.findById(id)
            .orElseThrow(() -> new ReceitaNaoEncontradaException(id));
        receita.setStatus(StatusReceita.PAGO_TOTAL);
        receita.setValorRecebido(receita.getValorFinal());
        receita.setDataRecebimentoReal(LocalDateTime.now());
        return receitaRepository.save(receita);
    }

    public Receita duplicar(UUID id) {
        var origem = receitaRepository.findById(id)
            .orElseThrow(() -> new ReceitaNaoEncontradaException(id));
        var copia = Receita.builder()
            .agendamentoId(null)
            .clienteId(origem.getClienteId())
            .clienteNome(origem.getClienteNome())
            .tipoServico(origem.getTipoServico())
            .descricao(origem.getDescricao())
            .valorBruto(origem.getValorBruto())
            .status(StatusReceita.PENDENTE)
            .valorRecebido(BigDecimal.ZERO)
            .dataPrevisaoRecebimento(null)
            .dataRecebimentoReal(null)
            .formaPagamento(origem.getFormaPagamento())
            .observacoes(origem.getObservacoes())
            .build();
        preencherComissao(copia);
        preencherStatus(copia, null);
        return receitaRepository.save(copia);
    }

    @Transactional(readOnly = true)
    public Receita buscarPorId(UUID id) {
        return receitaRepository.findById(id)
            .orElseThrow(() -> new ReceitaNaoEncontradaException(id));
    }

    @Transactional(readOnly = true)
    public List<Receita> listar(LocalDate dataInicio, LocalDate dataFim, StatusReceita status,
                                UUID clienteId, TipoServico tipoServico,
                                String formaPagamento, String sortBy, String sortDir) {
        Specification<Receita> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (dataInicio != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dataPrevisaoRecebimento"), dataInicio));
            }
            if (dataFim != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dataPrevisaoRecebimento"), dataFim));
            }
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (clienteId != null) predicates.add(cb.equal(root.get("clienteId"), clienteId));
            if (tipoServico != null) predicates.add(cb.equal(root.get("tipoServico"), tipoServico));
            if (formaPagamento != null && !formaPagamento.isBlank()) {
                try {
                    predicates.add(cb.equal(root.get("formaPagamento"),
                        com.photoizer.crm.shared.model.FormaPagamento.valueOf(formaPagamento)));
                } catch (IllegalArgumentException ignored) {
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        var coluna = switch (sortBy == null ? "" : sortBy) {
            case "valor" -> "valorBruto";
            case "cliente" -> "clienteNome";
            case "data" -> "dataPrevisaoRecebimento";
            default -> "dataPrevisaoRecebimento";
        };
        var direcao = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return receitaRepository.findAll(spec, Sort.by(direcao, coluna));
    }

    private void preencherCliente(Receita receita, ReceitaRequest request) {
        if (request.clienteId() == null) {
            throw new ClienteObrigatorioException();
        }
        var cliente = clienteRepository.findById(request.clienteId())
            .orElseThrow(() -> new com.photoizer.crm.cliente.exception.ClienteNaoEncontradoException(request.clienteId()));
        receita.setClienteId(cliente.getId());
        receita.setClienteNome(cliente.getNome());
    }

    private void preencherComissao(Receita receita) {
        var bruto = receita.getValorBruto();
        var percentual = configuracaoService.getValorDecimal(ConfigKey.PERCENTUAL_COMISSAO);

        var comissao = bruto.multiply(percentual).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        receita.setValorComissao(comissao);
        receita.setValorFinal(bruto.subtract(comissao).max(BigDecimal.ZERO));
    }

    private void preencherStatus(Receita receita, StatusReceita statusExplicito) {
        var valorRecebido = receita.getValorRecebido() != null ? receita.getValorRecebido() : BigDecimal.ZERO;
        if (valorRecebido.compareTo(receita.getValorFinal()) > 0) {
            throw new ValorRecebidoExcedeFinalException();
        }
        receita.setValorRecebido(valorRecebido);

        var status = statusExplicito != null
            ? statusExplicito
            : derivarStatus(valorRecebido, receita.getValorFinal());

        if (status == StatusReceita.PAGO_TOTAL) {
            receita.setValorRecebido(receita.getValorFinal());
            if (receita.getDataRecebimentoReal() == null) {
                receita.setDataRecebimentoReal(LocalDateTime.now());
            }
        }
        receita.setStatus(status);
    }

    private StatusReceita derivarStatus(BigDecimal recebido, BigDecimal valorFinal) {
        if (recebido.signum() == 0) return StatusReceita.PENDENTE;
        return recebido.compareTo(valorFinal) >= 0 ? StatusReceita.PAGO_TOTAL : StatusReceita.PAGO_PARCIAL;
    }
}
