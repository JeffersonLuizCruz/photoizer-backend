package com.photoizer.crm.financeiro.service;

import com.photoizer.crm.financeiro.repository.PagamentoRepository;
import com.photoizer.crm.shared.storage.ArquivosReferenciadosProvider;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/** Comprovantes anexados aos pagamentos financeiros. */
@Component
public class PagamentoArquivosReferenciadosProvider implements ArquivosReferenciadosProvider {

    private final PagamentoRepository pagamentoRepository;

    public PagamentoArquivosReferenciadosProvider(PagamentoRepository pagamentoRepository) {
        this.pagamentoRepository = pagamentoRepository;
    }

    @Override
    public Collection<String> caminhosReferenciados() {
        List<String> caminhos = pagamentoRepository.caminhosComprovante();
        return caminhos;
    }
}
