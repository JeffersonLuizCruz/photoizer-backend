package com.photoizer.crm.config.service;

import com.photoizer.crm.config.model.Configuracao;
import com.photoizer.crm.config.repository.ConfiguracaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
@Transactional
public class ConfiguracaoService {

    private final ConfiguracaoRepository configuracaoRepository;

    public ConfiguracaoService(ConfiguracaoRepository configuracaoRepository) {
        this.configuracaoRepository = configuracaoRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getConfig() {
        var configs = configuracaoRepository.findAll();
        var map = new HashMap<String, Object>();
        for (var c : configs) {
            map.put(c.getChave(), new BigDecimal(c.getValor()));
        }
        return map;
    }

    public void atualizarMultiplos(Map<String, String> valores) {
        for (var entry : valores.entrySet()) {
            var config = configuracaoRepository.findById(entry.getKey())
                .orElseGet(() -> {
                    var nova = new Configuracao();
                    nova.setChave(entry.getKey());
                    return nova;
                });
            config.setValor(entry.getValue());
            configuracaoRepository.save(config);
        }
    }

    @Transactional(readOnly = true)
    public BigDecimal getValorDecimal(String chave, BigDecimal valorPadrao) {
        return configuracaoRepository.findById(chave)
            .map(c -> new BigDecimal(c.getValor()))
            .orElse(valorPadrao);
    }
}
