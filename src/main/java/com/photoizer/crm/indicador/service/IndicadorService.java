package com.photoizer.crm.indicador.service;

import com.photoizer.crm.indicador.exception.IndicadorDuplicadoException;
import com.photoizer.crm.indicador.exception.IndicadorNaoEncontradoException;
import com.photoizer.crm.indicador.model.Indicador;
import com.photoizer.crm.indicador.repository.IndicadorRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Serviço de negócio do módulo indicador.
 *
 * Pattern: Command Object — criar() e atualizar() recebem IndicadorCommand
 * (record imutável) em vez de parâmetros posicionais, eliminando confusão
 * de ordem de argumentos e facilitando evolução (adicionar campos sem quebrar assinatura).
 *
 * Pattern: Domain Exceptions — erros de negócio usam exceções de domínio específicas
 * (IndicadorNaoEncontradoException, IndicadorDuplicadoException) em vez de
 * RuntimeException genérica, garantindo mapeamento correto para HTTP status.
 */
@Service
@Transactional
public class IndicadorService {

    private final IndicadorRepository indicadorRepository;

    public IndicadorService(IndicadorRepository indicadorRepository) {
        this.indicadorRepository = indicadorRepository;
    }

    @Transactional(readOnly = true)
    public Page<Indicador> listar(String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return indicadorRepository.search(search, pageable);
        }
        return indicadorRepository.findAllPaginated(pageable);
    }

    @Transactional(readOnly = true)
    public Indicador buscarPorId(UUID id) {
        return indicadorRepository.findById(id)
            .orElseThrow(() -> new IndicadorNaoEncontradoException(id));
    }

    /**
     * Cria um novo indicador. Lança conflito se nome+telefone já existir.
     * Para uso interno (upsert), usar buscarOuCriar().
     */
    public Indicador criar(IndicadorCommand command) {
        if (indicadorRepository.existsByNomeAndTelefone(command.nome(), command.telefone())) {
            throw new IndicadorDuplicadoException(command.nome(), command.telefone());
        }
        var indicador = Indicador.builder()
            .nome(command.nome())
            .telefone(command.telefone())
            .observacoes(command.observacoes())
            .percentualComissao(command.percentualComissao())
            .build();
        return indicadorRepository.save(indicador);
    }

    public Indicador atualizar(UUID id, IndicadorCommand command) {
        var indicador = buscarPorId(id);
        // Verifica se o novo nome+telefone pertence a OUTRO indicador
        indicadorRepository.findByNomeAndTelefone(command.nome(), command.telefone())
            .ifPresent(outro -> {
                if (!outro.getId().equals(id)) {
                    throw new IndicadorDuplicadoException(command.nome(), command.telefone());
                }
            });
        indicador.setNome(command.nome());
        indicador.setTelefone(command.telefone());
        indicador.setObservacoes(command.observacoes());
        indicador.setPercentualComissao(command.percentualComissao());
        return indicadorRepository.save(indicador);
    }

    public void remover(UUID id) {
        if (!indicadorRepository.existsById(id)) {
            throw new IndicadorNaoEncontradoException(id);
        }
        indicadorRepository.deleteById(id);
    }

    /**
     * Busca indicador por nome+telefone; se ausente, cria automaticamente.
     * Usado por módulos externos (comissao, financeiro) durante o fluxo de agendamento.
     *
     * Pattern: Optimistic Upsert — tenta criar direto; se o unique constraint
     * falhar (race condition), busca o registro que outro thread inseriu.
     */
    public Indicador buscarOuCriar(String nome, String telefone) {
        return indicadorRepository.findByNomeAndTelefone(nome, telefone)
            .orElseGet(() -> {
                try {
                    var indicador = Indicador.builder()
                        .nome(nome)
                        .telefone(telefone)
                        .build();
                    return indicadorRepository.save(indicador);
                } catch (DataIntegrityViolationException e) {
                    // Race condition: outro thread criou o registro entre findBy e save
                    return indicadorRepository.findByNomeAndTelefone(nome, telefone)
                        .orElseThrow(() -> new IndicadorNaoEncontradoException(nome));
                }
            });
    }
}
