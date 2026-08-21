# Módulo: Config

## 1. Responsabilidade
Gerencia configurações globais do sistema no modelo chave-valor. Consumido por `agenda`, `comissao`, `financeiro`, `contrato` e `despesa` para obter percentuais, taxas, prazos e template de contrato.

## 2. Estrutura
```
config/
├── model/
│   ├── Configuracao.java          # Entidade JPA (@Id String chave + String valor TEXT, sem auditoria)
│   └── ConfigKey.java             # Enum type-safe com chaves conhecidas + tipo + default
├── repository/
│   └── ConfiguracaoRepository.java # JpaRepository<Configuracao, String>
├── service/
│   └── ConfiguracaoService.java    # CRUD type-safe via ConfigKey + cache
├── api/
│   ├── ConfiguracaoController.java # GET/PUT /api/v1/config (DTOs)
│   ├── ConfiguracaoRequest.java    # Record DTO de entrada
│   └── ConfiguracaoResponse.java   # Record DTO de saída
└── exception/
    └── ConfiguracaoInvalidaException.java  # Exceção específica para valores inválidos
```

## 3. Dependências Externas

### Módulos internos importados
Nenhum. O módulo config é **fundacional** — não depende de módulos de domínio.

> **Refatoração (Fase 2):** A dependência `config→contrato` (ContratoTemplateService) foi removida.
> Endpoints de template de contrato foram movidos para `contrato/api/ContratoTemplateController.java`.

### Módulos que dependem deste
| Módulo | Uso |
|--------|-----|
| agenda | `getValorDecimal(ConfigKey.TAXA_DESLOCAMENTO/PERCENTUAL_ENTRADA)` |
| comissao | (via financeiro) |
| financeiro | `getValorDecimal(ConfigKey.PERCENTUAL_ENTRADA/PERCENTUAL_COMISSAO)` |
| contrato | `getValor(ConfigKey.*)`, `getValorDecimal`, `getValorInteiro`, `atualizar` |
| despesa | categorias/valores |
| ecommerce | `getValorDecimal(ConfigKey.VALOR_FOTO_EXTRA)` |

### Eventos
Nenhum. Não publica nem consome eventos.

## 4. Fluxos Principais

### Fluxo 1: Leitura
- `GET /api/v1/config` → `ConfiguracaoResponse` com todas as chaves (`getConfig`).
- `getValor(ConfigKey)` → busca por chave, retorna valor bruto ou default do enum.
- `getValorDecimal(ConfigKey)` / `getValorInteiro(ConfigKey)` → busca + conversão tipada.

### Fluxo 2: Atualização
- `PUT /api/v1/config` → `ConfiguracaoRequest` com `Map<ConfigKey, String>`; upsert chave a chave com validação de tipo.
- `atualizar(ConfigKey, String)` → upsert individual com validação.

### Chaves conhecidas (centralizadas no `ConfigKey` enum)
| Constante | Chave no banco | Tipo | Default |
|-----------|---------------|------|---------|
| `VALOR_FOTO_EXTRA` | `valorUnitarioFotoExtra` | DECIMAL | `15.00` |
| `VALOR_VIDEO_EXTRA` | `valorUnitarioVideoExtra` | DECIMAL | `50.00` |
| `PERCENTUAL_COMISSAO` | `percentualComissao` | DECIMAL | `10.00` |
| `PERCENTUAL_ENTRADA` | `percentualEntrada` | DECIMAL | `30.00` |
| `TAXA_DESLOCAMENTO` | `taxaDeslocamentoPadrao` | DECIMAL | `0.00` |
| `NOME_CONTRATADA` | `nomeContratada` | TEXT | `Carol Oliva Fotografia` |
| `CNPJ_CONTRATADA` | `cnpjContratada` | TEXT | `""` |
| `ENDERECO_CONTRATADA` | `enderecoContratada` | TEXT | `""` |
| `PIX_CHAVE` | `pixChave` | TEXT | `""` |
| `PIX_TIPO_CHAVE` | `pixTipoChave` | TEXT | `CNPJ` |
| `CONTRATO_DIAS_VALIDADE` | `contratoDiasValidade` | INTEGER | `7` |
| `CONTRATO_TEMPLATE` | `contratoTemplateTexto` | TEXT | `null` |

## 5. Regras Específicas

1. **`ConfigKey` centraliza tudo**: chaves, tipos e defaults. Consumidores usam `ConfigKey.XXX` em vez de strings.
2. **Validação de tipo**: `ConfigKey.convert()` valida se o valor é compatível com o tipo (DECIMAL/INTEGER/TEXT) e lança `ConfiguracaoInvalidaException` se inválido.
3. **Cache**: `@Cacheable("config")` nas leituras, `@CacheEvict("config")` nas escritas. Usa `ConcurrentMapCacheManager` (sem dependência externa).
4. **Upsert implícito**: qualquer chave enviada no `PUT` é criada, com validação de tipo via ConfigKey.
5. **Sem auditoria nem BaseEntity**: `Configuracao` é chave-valor mínimo.

## 6. Testes
Nenhum teste específico.

## 7. Débitas Resolvidas (Fase 2)

| Débita | Solução |
|--------|---------|
| **7.1 Validação de chaves e tipos** | `ConfigKey` enum com tipos + `ConfiguracaoInvalidaException` |
| **7.2 Sem cache** | `@Cacheable`/`@CacheEvict` no `ConfiguracaoService` |
| **7.3 Cruzamento config→contrato na controller** | Endpoints movidos para `ContratoTemplateController` (módulo contrato) |
| **7.4 Controller sem DTO e sem validação** | `ConfiguracaoRequest`/`ConfiguracaoResponse` com `@Valid` |
| **7.5 Duplicidade de nomes de chaves** | `ConfigKey` centraliza todos os nomes; `DataSeeder` usa o enum |
| **7.6 `getValorTexto` com tamanho ilimitado** | Mantido; `atualizarValorTexto` mantido deprecated para compatibilidade |

## 8. Exemplos de arquivos afetados
- `ConfiguracaoService.java` — refatorado: métodos type-safe, cache, deprecated para legados
- `ConfiguracaoController.java` — refatorado: DTOs, sem cross-module
- `ConfigKey.java` — novo: enum centralizador
- `ConfiguracaoInvalidaException.java` — novo: exceção específica
- `ConfiguracaoRequest.java` / `ConfiguracaoResponse.java` — novos: DTOs
- `ContratoTemplateController.java` (módulo contrato) — novo: endpoints de template
- `ContratoTemplateService.java` (módulo contrato) — editado: `TEMPLATO_PADRAO` movido aqui
- `DataSeeder.java` — editado: usa `ConfigKey`
- Consumidores (6 arquivos) — editados: usam `ConfigKey` em vez de strings
