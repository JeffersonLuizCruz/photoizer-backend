package com.photoizer.crm.config.model;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Type-Safe Enum que centraliza todas as chaves de configuracao conhecidas pelo sistema.
 *
 * <p>Pattern: Type-Safe Enum — elimina string literals espalhados no codigo,
 * provedefaults centralizados e permite validacao compile-time.
 *
 * <p>Cada constante define:
 * <ul>
 *   <li>{@code key} — nome da chave armazenada no banco (tabela configuracoes)</li>
 *   <li>{@code type} — tipo esperado do valor (DECIMAL, INTEGER, TEXT)</li>
 *   <li>{@code defaultValue} — valor padrao retornado quando a chave nao existe no banco</li>
 * </ul>
 *
 * <p>Consumidores devem usar {@code ConfigKey.PERCENTUAL_ENTRADA} em vez de
 * {@code "percentualEntrada"} para garantir seguranca de tipos e evitar erros de runtime.
 */
public enum ConfigKey {

    // ── Financeiro ──────────────────────────────────────────────
    VALOR_FOTO_EXTRA("valorUnitarioFotoExtra", Type.DECIMAL, "15.00"),
    VALOR_VIDEO_EXTRA("valorUnitarioVideoExtra", Type.DECIMAL, "50.00"),

    // ── Comissao ────────────────────────────────────────────────
    PERCENTUAL_COMISSAO("percentualComissao", Type.DECIMAL, "10.00"),

    // ── Agenda / Contrato ───────────────────────────────────────
    PERCENTUAL_ENTRADA("percentualEntrada", Type.DECIMAL, "30.00"),
    TAXA_DESLOCAMENTO("taxaDeslocamentoPadrao", Type.DECIMAL, "0.00"),

    // ── Dados da contratada ─────────────────────────────────────
    NOME_CONTRATADA("nomeContratada", Type.TEXT, "Carol Oliva Fotografia"),
    CNPJ_CONTRATADA("cnpjContratada", Type.TEXT, ""),
    ENDERECO_CONTRATADA("enderecoContratada", Type.TEXT, ""),
    PIX_CHAVE("pixChave", Type.TEXT, ""),
    PIX_TIPO_CHAVE("pixTipoChave", Type.TEXT, "CNPJ"),

    // ── Contrato ────────────────────────────────────────────────
    CONTRATO_DIAS_VALIDADE("contratoDiasValidade", Type.INTEGER, "7"),
    CONTRATO_TEMPLATE("contratoTemplateTexto", Type.TEXT, null);

    /**
     * Template padrao do contrato de prestacao de servicos fotograficos.
     * Utilizado pelo ConfigContratoSeeder e pelo endpoint de restauracao de template.
     */
    public static final String CONTRATO_TEMPLATE_PADRAO = """
= PRESTAÇÃO DE SERVIÇOS FOTOGRÁFICOS =

# 1. Dados do Cliente
Nome completo: {{clienteNome}}
CPF: {{clienteCPF}}
E-mail: {{clienteEmail}}
Telefone: {{clienteTelefone}}
Cidade / Estado: {{clienteCidade}} / {{clienteEstado}}

Contratada: {{contratadaNome}}, inscrita no CNPJ nº {{contratadaCnpj}}, com sede em {{contratadaCidade}}.

# 2. Informações do Ensaio
Data do ensaio: {{dataEnsaio}}
Horário do ensaio: {{horarioEnsaio}}
Local do ensaio: {{localEnsaio}}
Endereço completo: {{enderecoEnsaio}}
Profissionais do ensaio: {{fotografosEnsaio}}

# 3. Pacote Contratado
Pacote: {{pacoteNome}}
Inclui: serviço conforme pacote contratado.

# 4. Valores
Valor total do serviço: {{valorTotal}}
Valor pago como reserva ({{percentualEntrada}}%): {{valorEntrada}}
Valor restante a pagar no final do ensaio: {{valorRestante}}
O pagamento da reserva garante o bloqueio da data e horário na agenda da Contratada.
O valor restante deverá ser pago ao final da realização do ensaio fotográfico.

Dados para pagamento (PIX)
Chave PIX ({{pixTipoChave}}): {{pixChave}}

# 5. Entrega das Fotografias
As fotos do ensaio serão enviadas ao Cliente em até 2 dias após a realização do ensaio para que ele faça a seleção das imagens desejadas.
Após a seleção, a entrega final das fotografias ocorrerá em até 2 dias.
As fotos serão entregues em formato digital, em alta resolução.
Caso o Cliente opte por fotos extras além do pacote contratado, será cobrado o valor de {{precoFotoExtra}} por foto adicional.

# 6. Cancelamento
Caso o Cliente cancele o ensaio por qualquer motivo, o valor pago como reserva não será reembolsado, pois garante a reserva da data na agenda da Contratada.
Caso ocorra algum imprevisto que impeça a presença da Contratada, poderá haver a substituição por outro fotógrafo profissional de padrão equivalente.
Caso não seja possível a substituição, o valor pago será devolvido integralmente ao Cliente.
Se houver algum imprevisto relacionado à antecipação de voo, chuva ou doença, o ensaio será cancelado e haverá o reembolso completo do valor da reserva.

# 7. USO DE IMAGEM (OPCIONAL)
{{autorizaUsoImagem}}

# 8. Disposições Gerais
Este contrato passa a vigorar a partir da assinatura das partes.
Qualquer alteração neste contrato deverá ser realizada por escrito.

# 9. Assinatura Digital
Ao assinar este documento, o Cliente declara que leu e concorda com todos os termos acima descritos.
Resposta do cliente sobre uso de imagem: {{autorizaUsoImagem}}
Assinatura do contratante: {{clienteNome}}
Assinatura da Contratada: {{contratadaNome}}
""";

    /**
     * Tipo esperado do valor da configuracao.
     */
    public enum Type {
        DECIMAL,
        INTEGER,
        TEXT
    }

    private final String key;
    private final Type type;
    private final String defaultValue;

    // Mapa de lookup por nome da chave (rapido, O(1))
    private static final Map<String, ConfigKey> BY_KEY = Arrays.stream(values())
        .collect(Collectors.toMap(ConfigKey::getKey, Function.identity()));

    ConfigKey(String key, Type type, String defaultValue) {
        this.key = key;
        this.type = type;
        this.defaultValue = defaultValue;
    }

    public String getKey() {
        return key;
    }

    public Type getType() {
        return type;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     * Busca ConfigKey pelo nome da chave armazenada no banco.
     * Util para migrar codigo que usa string literal.
     */
    public static Optional<ConfigKey> ofKey(String key) {
        return Optional.ofNullable(BY_KEY.get(key));
    }

    /**
     * Converte o valor string para o tipo esperado pelo ConfigKey.
     *
     * @param valor string armazenada no banco
     * @return objeto convertido (BigDecimal, Integer ou String)
     * @throws com.photoizer.crm.config.exception.ConfiguracaoInvalidaException se valor for nulo/vazio
     *         ou incompativel com o tipo
     */
    public Object convert(String valor) {
        if (valor == null || valor.isBlank()) {
            if (defaultValue != null) {
                return convert(defaultValue);
            }
            throw new com.photoizer.crm.config.exception.ConfiguracaoInvalidaException(
                this, "valor ausente e sem default");
        }
        return switch (type) {
            case DECIMAL -> {
                try {
                    yield new BigDecimal(valor.trim());
                } catch (NumberFormatException e) {
                    throw new com.photoizer.crm.config.exception.ConfiguracaoInvalidaException(
                        this, "valor '" + valor + "' nao e um decimal valido");
                }
            }
            case INTEGER -> {
                try {
                    yield Integer.parseInt(valor.trim());
                } catch (NumberFormatException e) {
                    throw new com.photoizer.crm.config.exception.ConfiguracaoInvalidaException(
                        this, "valor '" + valor + "' nao e um inteiro valido");
                }
            }
            case TEXT -> valor;
        };
    }

    /**
     * Converte e retorna como BigDecimal. Util para as chaves DECIMAL.
     */
    public BigDecimal convertDecimal(String valor) {
        return (BigDecimal) convert(valor);
    }

    /**
     * Converte e retorna como Integer. Util para as chaves INTEGER.
     */
    public Integer convertInteger(String valor) {
        return (Integer) convert(valor);
    }
}
