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

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ConfiguracaoService.class);

    private final ConfiguracaoRepository configuracaoRepository;

    public ConfiguracaoService(ConfiguracaoRepository configuracaoRepository) {
        this.configuracaoRepository = configuracaoRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getConfig() {
        var configs = configuracaoRepository.findAll();
        var map = new HashMap<String, Object>();
        for (var c : configs) {
            map.put(c.getChave(), c.getValor());
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

    @Transactional(readOnly = true)
    public int getValorInteiro(String chave, int valorPadrao) {
        return configuracaoRepository.findById(chave)
            .map(c -> Integer.parseInt(c.getValor()))
            .orElse(valorPadrao);
    }

    @Transactional(readOnly = true)
    public String getValorTexto(String chave, String valorPadrao) {
        return configuracaoRepository.findById(chave)
            .map(c -> c.getValor())
            .filter(v -> v != null && !v.isBlank())
            .orElse(valorPadrao);
    }

    public void atualizarValorTexto(String chave, String valor) {
        var config = configuracaoRepository.findById(chave)
            .orElseGet(() -> {
                var nova = new com.photoizer.crm.config.model.Configuracao();
                nova.setChave(chave);
                return nova;
            });
        config.setValor(valor);
        configuracaoRepository.save(config);
        log.info("Configuracao atualizada: {} = {} ({} chars)", chave, valor.length() > 100 ? valor.substring(0, 100) + "..." : valor, valor.length());
    }
}
