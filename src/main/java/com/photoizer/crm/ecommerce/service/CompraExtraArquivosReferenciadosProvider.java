package com.photoizer.crm.ecommerce.service;

import com.photoizer.crm.ecommerce.repository.CompraExtraRepository;
import com.photoizer.crm.shared.storage.ArquivosReferenciadosProvider;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/** Comprovantes enviados nas compras de extras. */
@Component
public class CompraExtraArquivosReferenciadosProvider implements ArquivosReferenciadosProvider {

    private final CompraExtraRepository compraExtraRepository;

    public CompraExtraArquivosReferenciadosProvider(CompraExtraRepository compraExtraRepository) {
        this.compraExtraRepository = compraExtraRepository;
    }

    @Override
    public Collection<String> caminhosReferenciados() {
        List<String> caminhos = compraExtraRepository.caminhosComprovante();
        return caminhos;
    }
}
