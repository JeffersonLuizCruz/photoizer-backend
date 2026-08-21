package com.photoizer.crm.config.service;

import com.photoizer.crm.config.exception.ConfiguracaoInvalidaException;
import com.photoizer.crm.config.model.ConfigKey;
import com.photoizer.crm.config.model.Configuracao;
import com.photoizer.crm.config.repository.ConfiguracaoRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Service de configuracoes globais do sistema.
 *
 * <p>Oferece metodos type-safe via {@link ConfigKey} e metodos legados (deprecated)
 * que usam string literal para migracao gradual dos consumidores.
 *
 * <p>Cache: {@code @Cacheable("config")} nas leituras, {@code @CacheEvict} nas escritas.
 * Usa ConcurrentMapCacheManager (sem dependencia externa).
 */
@Service
@Transactional
public class ConfiguracaoService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ConfiguracaoService.class);

    private final ConfiguracaoRepository configuracaoRepository;

    public ConfiguracaoService(ConfiguracaoRepository configuracaoRepository) {
        this.configuracaoRepository = configuracaoRepository;
    }

    // ════════════════════════════════════════════════════════════════
    //  LEITURA — Type-safe (novo)
    // ════════════════════════════════════════════════════════════════

    /**
     * Retorna todas as configuracoes como Map<String, String>.
     */
    @Transactional(readOnly = true)
    @Cacheable("config")
    public Map<String, String> getConfig() {
        var configs = configuracaoRepository.findAll();
        var map = new HashMap<String, String>();
        for (var c : configs) {
            map.put(c.getChave(), c.getValor());
        }
        return map;
    }

    /**
     * Leitura generica — retorna o valor bruto como String.
     */
    @Transactional(readOnly = true)
    public String getValor(ConfigKey key) {
        return configuracaoRepository.findById(key.getKey())
            .map(Configuracao::getValor)
            .filter(v -> v != null && !v.isBlank())
            .orElse(key.getDefaultValue());
    }

    /**
     * Leitura decimal — valida tipo e converte.
     */
    @Transactional(readOnly = true)
    public BigDecimal getValorDecimal(ConfigKey key) {
        var valor = getValor(key);
        return key.convertDecimal(valor);
    }

    /**
     * Leitura inteira — valida tipo e converte.
     */
    @Transactional(readOnly = true)
    public Integer getValorInteiro(ConfigKey key) {
        var valor = getValor(key);
        return key.convertInteger(valor);
    }

    // ════════════════════════════════════════════════════════════════
    //  ESCRITA — Type-safe (novo)
    // ════════════════════════════════════════════════════════════════

    /**
     * Atualiza multiplos valores validando chaves e tipos via ConfigKey.
     * Aceita Map<String, String> para compatibilidade com o frontend.
     */
    @CacheEvict("config")
    public void atualizarMultiplos(Map<String, String> valores) {
        for (var entry : valores.entrySet()) {
            var keyStr = entry.getKey();
            var valor = entry.getValue();
            var key = ConfigKey.ofKey(keyStr)
                .orElseThrow(() -> new ConfiguracaoInvalidaException(keyStr, "chave nao reconhecida"));
            // Valida se o valor e compativel com o tipo do ConfigKey
            key.convert(valor);
            saveOrCreate(key.getKey(), valor);
        }
        log.info("Configuracoes atualizadas: {} chaves", valores.size());
    }

    /**
     * Atualiza um valor de configuracao validando o tipo.
     */
    @CacheEvict("config")
    public void atualizar(ConfigKey key, String valor) {
        key.convert(valor);
        saveOrCreate(key.getKey(), valor);
        log.info("Configuracao atualizada: {} = {}", key.name(),
            valor.length() > 100 ? valor.substring(0, 100) + "..." : valor);
    }

    // ════════════════════════════════════════════════════════════════
    //  LEGADOS — Deprecated (manter para migracao gradual)
    // ════════════════════════════════════════════════════════════════

    /**
     * @deprecated Use {@link #getValorDecimal(ConfigKey)}.
     */
    @Deprecated
    @Transactional(readOnly = true)
    public BigDecimal getValorDecimal(String chave, BigDecimal valorPadrao) {
        return configuracaoRepository.findById(chave)
            .map(c -> {
                try {
                    return new BigDecimal(c.getValor());
                } catch (NumberFormatException e) {
                    log.warn("Valor invalido para chave '{}': '{}' — retornando padrao", chave, c.getValor());
                    return valorPadrao;
                }
            })
            .orElse(valorPadrao);
    }

    /**
     * @deprecated Use {@link #getValorInteiro(ConfigKey)}.
     */
    @Deprecated
    @Transactional(readOnly = true)
    public int getValorInteiro(String chave, int valorPadrao) {
        return configuracaoRepository.findById(chave)
            .map(c -> {
                try {
                    return Integer.parseInt(c.getValor());
                } catch (NumberFormatException e) {
                    log.warn("Valor invalido para chave '{}': '{}' — retornando padrao", chave, c.getValor());
                    return valorPadrao;
                }
            })
            .orElse(valorPadrao);
    }

    /**
     * @deprecated Use {@link #getValor(ConfigKey)}.
     */
    @Deprecated
    @Transactional(readOnly = true)
    public String getValorTexto(String chave, String valorPadrao) {
        return configuracaoRepository.findById(chave)
            .map(Configuracao::getValor)
            .filter(v -> v != null && !v.isBlank())
            .orElse(valorPadrao);
    }

    /**
     * @deprecated Use {@link #atualizar(ConfigKey, String)}.
     */
    @Deprecated
    @CacheEvict("config")
    public void atualizarValorTexto(String chave, String valor) {
        saveOrCreate(chave, valor);
        log.info("Configuracao atualizada: {} = {} ({} chars)", chave,
            valor.length() > 100 ? valor.substring(0, 100) + "..." : valor, valor.length());
    }

    // ════════════════════════════════════════════════════════════════
    //  PRIVADOS
    // ════════════════════════════════════════════════════════════════

    private void saveOrCreate(String chave, String valor) {
        var config = configuracaoRepository.findById(chave)
            .orElseGet(() -> {
                var nova = new Configuracao();
                nova.setChave(chave);
                return nova;
            });
        config.setValor(valor);
        configuracaoRepository.save(config);
    }
}
