package com.photoizer.crm.config.service;

import com.photoizer.crm.config.model.ConfigKey;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Texto central da marca d'agua das previsoes publicas.
 *
 * <p>O nome vem das Configuracoes (Dados da Contratada). Quando ainda nao foi
 * preenchido, usa o nome do fotografo e, por ultimo, um fallback neutro.
 * As fotos antigas mantem a marca gravada no arquivo; este texto vale para
 * novas fotos, novas edicoes e para a regeneracao manual das fotos existentes.
 */
@Service
public class MarcaDaguaService {

    public static final float OPACIDADE_PADRAO = 0.35f;
    private static final String FALLBACK = "Estúdio";

    private final ConfiguracaoService configuracaoService;

    public MarcaDaguaService(ConfiguracaoService configuracaoService) {
        this.configuracaoService = configuracaoService;
    }

    public String texto() {
        var config = configuracaoService.getConfig();
        var nome = valor(config, ConfigKey.NOME_CONTRATADA);
        if (nome.isBlank()) {
            nome = valor(config, ConfigKey.NOME_FOTOGRAFO);
        }
        if (nome.isBlank()) {
            nome = FALLBACK;
        }
        return "© " + nome.trim();
    }

    public float opacidade() {
        return OPACIDADE_PADRAO;
    }

    private String valor(Map<String, String> config, ConfigKey key) {
        var atual = config.get(key.getKey());
        if (atual != null && !atual.isBlank()) {
            return atual;
        }
        var padrao = key.getDefaultValue();
        return padrao == null ? "" : padrao;
    }
}
