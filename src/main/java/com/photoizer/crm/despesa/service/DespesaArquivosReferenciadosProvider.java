package com.photoizer.crm.despesa.service;

import com.photoizer.crm.despesa.repository.DespesaRepository;
import com.photoizer.crm.shared.storage.ArquivosReferenciadosProvider;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/** Comprovantes anexados às despesas. */
@Component
public class DespesaArquivosReferenciadosProvider implements ArquivosReferenciadosProvider {

    private final DespesaRepository despesaRepository;

    public DespesaArquivosReferenciadosProvider(DespesaRepository despesaRepository) {
        this.despesaRepository = despesaRepository;
    }

    @Override
    public Collection<String> caminhosReferenciados() {
        List<String> caminhos = despesaRepository.caminhosComprovante();
        return caminhos;
    }
}
