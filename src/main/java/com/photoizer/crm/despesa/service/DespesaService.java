package com.photoizer.crm.despesa.service;

import com.photoizer.crm.despesa.model.Despesa;
import com.photoizer.crm.despesa.repository.DespesaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class DespesaService {

    private final DespesaRepository despesaRepository;

    public DespesaService(DespesaRepository despesaRepository) {
        this.despesaRepository = despesaRepository;
    }

    @Transactional(readOnly = true)
    public List<Despesa> listar(LocalDate dataInicio, LocalDate dataFim) {
        if (dataInicio != null && dataFim != null) {
            return despesaRepository.findByDataBetweenOrderByDataDesc(dataInicio, dataFim);
        }
        return despesaRepository.findAllByOrderByDataDesc();
    }

    @Transactional(readOnly = true)
    public Despesa buscarPorId(UUID id) {
        return despesaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Despesa não encontrada: " + id));
    }

    public Despesa criar(String descricao, BigDecimal valor, String categoria, LocalDate data, String observacao) {
        var despesa = Despesa.builder()
            .descricao(descricao)
            .valor(valor)
            .categoria(categoria)
            .data(data)
            .observacao(observacao)
            .build();
        return despesaRepository.save(despesa);
    }

    public Despesa atualizar(UUID id, String descricao, BigDecimal valor, String categoria, LocalDate data, String observacao) {
        var despesa = buscarPorId(id);
        despesa.setDescricao(descricao);
        despesa.setValor(valor);
        despesa.setCategoria(categoria);
        despesa.setData(data);
        despesa.setObservacao(observacao);
        return despesaRepository.save(despesa);
    }

    public void remover(UUID id) {
        if (!despesaRepository.existsById(id)) {
            throw new RuntimeException("Despesa não encontrada: " + id);
        }
        despesaRepository.deleteById(id);
    }
}
