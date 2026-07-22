package com.photoizer.crm.indicador.service;

import com.photoizer.crm.indicador.model.Indicador;
import com.photoizer.crm.indicador.repository.IndicadorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public Indicador criar(String nome, String telefone, String observacoes) {
        var indicador = Indicador.builder()
            .nome(nome)
            .telefone(telefone)
            .observacoes(observacoes)
            .build();
        return indicadorRepository.save(indicador);
    }

    public Indicador atualizar(UUID id, String nome, String telefone, String observacoes) {
        var indicador = buscarPorId(id);
        indicador.setNome(nome);
        indicador.setTelefone(telefone);
        indicador.setObservacoes(observacoes);
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
            .orElseGet(() -> criar(nome, telefone, null));
    }
}
