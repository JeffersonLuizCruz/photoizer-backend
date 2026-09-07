package com.photoizer.crm.config.exception;

import com.photoizer.crm.config.model.ConfigKey;
import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.UnprocessableException;

/**
 * Excecao lancada quando uma configuracao e invalida:
 * - valor incompativel com o tipo esperado (ex: texto em campo decimal)
 * - chave nao reconhecida no ConfigKey
 */
public class ConfiguracaoInvalidaException extends UnprocessableException {

    private final String chave;

    public ConfiguracaoInvalidaException(String chave, String motivo) {
        super(ErrorCode.CONFIGURACAO_INVALIDA, "Configuracao invalida para chave '" + chave + "': " + motivo);
        this.chave = chave;
    }

    public ConfiguracaoInvalidaException(ConfigKey key, String motivo) {
        this(key.name(), motivo);
    }

    public String getChave() {
        return chave;
    }
}
