package com.photoizer.crm.config.service;

import com.photoizer.crm.config.model.ConfigKey;
import com.photoizer.crm.shared.storage.ArquivosReferenciadosProvider;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/** Arquivos referenciados por configurações (logo da contratada). */
@Component
public class ConfiguracaoArquivosReferenciadosProvider implements ArquivosReferenciadosProvider {

    private final ConfiguracaoService configuracaoService;

    public ConfiguracaoArquivosReferenciadosProvider(ConfiguracaoService configuracaoService) {
        this.configuracaoService = configuracaoService;
    }

    @Override
    public Collection<String> caminhosReferenciados() {
        var logo = configuracaoService.getValor(ConfigKey.LOGO_CONTRATADA);
        return logo == null || logo.isBlank() ? List.of() : List.of(logo);
    }
}
