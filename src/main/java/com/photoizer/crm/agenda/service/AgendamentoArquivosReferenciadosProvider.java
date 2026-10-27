package com.photoizer.crm.agenda.service;

import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.shared.storage.ArquivosReferenciadosProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Arquivos referenciados pelo módulo agenda: comprovantes de entrada/final,
 * termo assinado e assinatura manuscrita (legado).
 */
@Component
public class AgendamentoArquivosReferenciadosProvider implements ArquivosReferenciadosProvider {

    private final AgendamentoRepository agendamentoRepository;

    public AgendamentoArquivosReferenciadosProvider(AgendamentoRepository agendamentoRepository) {
        this.agendamentoRepository = agendamentoRepository;
    }

    @Override
    public Collection<String> caminhosReferenciados() {
        var caminhos = new ArrayList<String>();
        for (var registro : agendamentoRepository.caminhosDeArquivo()) {
            adicionar(caminhos, registro.getUrlComprovanteEntrada());
            adicionar(caminhos, registro.getUrlComprovanteFinal());
            adicionar(caminhos, registro.getUrlPdfAssinatura());
            adicionar(caminhos, registro.getUrlAssinaturaImagem());
        }
        return caminhos;
    }

    private void adicionar(Collection<String> caminhos, String caminho) {
        if (caminho != null && !caminho.isBlank()) {
            caminhos.add(caminho);
        }
    }
}
