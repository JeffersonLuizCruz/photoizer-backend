package com.photoizer.crm.indicador.service;

import com.photoizer.crm.indicador.model.Indicador;
import com.photoizer.crm.indicador.repository.IndicadorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class IndicadorService {

    private final IndicadorRepository indicadorRepository;

    public IndicadorService(IndicadorRepository indicadorRepository) {
        this.indicadorRepository = indicadorRepository;
    }

    @Transactional(readOnly = true)
    public List<Indicador> listar(String search) {
        if (search != null && !search.isBlank()) {
            return indicadorRepository.search(search);
        }
        return indicadorRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Indicador buscarPorId(UUID id) {
        return indicadorRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Indicador não encontrado: " + id));
    }

    public Indicador criar(String nome, String telefone, String observacoes, BigDecimal percentualComissao) {
        var existente = indicadorRepository.findByNomeAndTelefone(nome, telefone);
        if (existente.isPresent()) {
            var indicador = existente.get();
            if (observacoes != null) indicador.setObservacoes(observacoes);
            if (percentualComissao != null) indicador.setPercentualComissao(percentualComissao);
            return indicadorRepository.save(indicador);
        }
        var indicador = Indicador.builder()
            .nome(nome)
            .telefone(telefone)
            .observacoes(observacoes)
            .percentualComissao(percentualComissao)
            .build();
        return indicadorRepository.save(indicador);
    }

    public Indicador atualizar(UUID id, String nome, String telefone, String observacoes, BigDecimal percentualComissao) {
        var indicador = buscarPorId(id);
        indicador.setNome(nome);
        indicador.setTelefone(telefone);
        indicador.setObservacoes(observacoes);
        indicador.setPercentualComissao(percentualComissao);
        return indicadorRepository.save(indicador);
    }

    public void remover(UUID id) {
        if (!indicadorRepository.existsById(id)) {
            throw new RuntimeException("Indicador não encontrado: " + id);
        }
        indicadorRepository.deleteById(id);
    }

    public Indicador buscarOuCriar(String nome, String telefone) {
        return indicadorRepository.findByNomeAndTelefone(nome, telefone)
            .orElseGet(() -> {
                try {
                    return criar(nome, telefone, null, null);
                } catch (Exception e) {
                    return indicadorRepository.findByNomeAndTelefone(nome, telefone)
                        .orElseThrow(() -> new RuntimeException("Falha ao criar/recuperar indicador: " + nome));
                }
            });
    }
}
