# Módulo: Config

## 1. Responsabilidade
Gerencia configurações globais do sistema no modelo chave-valor. Consumido por `agenda`, `comissao`, `financeiro`, `contrato` e `despesa` para obter percentuais, taxas, prazos e template de contrato.

## 2. Estrutura
```
config/
├── model/
│   └── Configuracao.java         # Entidade JPA (@Id String chave + String valor TEXT, sem auditoria)
├── repository/
│   └── ConfiguracaoRepository.java # JpaRepository<Configuracao, String>
├── service/
│   └── ConfiguracaoService.java    # getConfig, atualizarMultiplos, getValorDecimal/Inteiro/Texto, atualizarValorTexto
└── api/
    └── ConfiguracaoController.java # GET/PUT /api/v1/config + endpoints de template de contrato
```

## 3. Dependências Externas

### Módulos internos importados — **[VIOLAÇÃO Modulith]**
- `ConfiguracaoController.java:21-27` injeta `ContratoTemplateService` do módulo **contrato** e acessa `shared.config.DataSeeder.TEMPLATO_PADRAO` (`:62-63`). Um módulo "fundacional" de config dependendo de `contrato` une dois domínios distintos na mesma controller.

### Módulos que dependem deste
| Módulo | Uso |
|--------|-----|
| agenda | `getValorDecimal("percentualEntrada"/"taxaDeslocamentoPadrao")` |
| comissao | `getValorDecimal("comissaoPercentual"/"percentualComissao")` |
| financeiro | `getValorDecimal` para valores de foto/vídeo extra |
| contrato | `getValorTexto` para dados da contratada e template |
| despesa | categorias/valores |

### Eventos
Nenhum. Não publica nem consome eventos.

## 4. Fluxos Principais

### Fluxo 1: Leitura
- `GET /api/v1/config` → `Map<String, Object>` com todas as chaves (`getConfig`).
- `getValorDecimal/Inteiro/Texto(chave, default)` → busca por chave e converte; retorna default se ausente.

### Fluxo 2: Atualização
- `PUT /api/v1/config` → `Map<String, String>`; upsert chave a chave (`atualizarMultiplos`).
- Endpoints `/contrato/template/**` delegam a `ContratoTemplateService` + `atualizarValorTexto`.

### Chaves conhecidas (populadas pelo `DataSeeder` e seeds de contrato)
| Chave | Tipo | Descrição |
|-------|------|-----------|
| `valorUnitarioFotoExtra` / `valorUnitarioVideoExtra` | decimal | Preços de extras |
| `percentualComissao` | decimal | Comissão padrão de indicação |
| `percentualEntrada` | decimal | % de entrada cobrado no agendamento |
| `taxaDeslocamentoPadrao` | decimal | Taxa de deslocamento |
| `nomeContratada`, `cnpjContratada`, `enderecoContratada`, `pixChave`, `pixTipoChave`, `contratoDiasValidade` | texto | Dados do contrato |
| `contratoTemplateTexto` | texto | Template com placeholders `{{...}}` |

**Nota**: o `DataSeeder` (shared) registra as chaves com nomes distintos dos citados no `AGENTS.md` (`comissao_percentual_padrao`, etc.) — há **duplicidade de nomenclatura de chaves** entre consumidores.

## 5. Regras Específicas
1. **Tudo é String**: conversões via `new BigDecimal(...)`/`Integer.parseInt(...)` sem tratamento de erro.
2. **Sem auditoria nem BaseEntity**: `Configuration` é chave-valor mínimo.
3. **Upsert implícito**: qualquer chave enviada no `PUT` é criada, sem validação de schema.
4. **`getValorDecimal`/`getValorInteiro` podem quebrar**: se o valor armazenado não for numérico, `NumberFormatException` → handler 422 genérico.
5. **Sem cache**: toda leitura consulta o banco.

## 6. Testes
Nenhum teste específico.

## 7. Dívidas Técnicas e Melhorias Recomendadas

### 7.1 Vaização de chaves e tipos — **P1**
- `PUT /api/v1/config` aceita qualquer `Map<String,String>` (`ConfiguracaoController.java:37-39`), criando chaves órfãs ou com valor inválido. `getValorDecimal` lança `NumberFormatException` silenciosamente (`ConfiguracaoService.java:48-52`).
- **Solução**: definir **enum `ConfigKey`** com as chaves conhecidas + tipo esperado (DECIMAL, INT, TEXT, TEMPLATE). O service remove a necessidade de string mágica e valida conversão com resposta clara. Isso elimina o acoplamento por string hardcoded nos módulos consumidores.

### 7.2 Sem cache — **P2**
- Configurações lidas em alta frequência (percentual de entrada, taxas). Cada `getValor*` faz `findById` (`ConfiguracaoService.java:48-67`).
- **Solução**: cache `@Cacheable("config")` + invalidação no `atualizarMultiplos`/`atualizarValorTexto` (Spring Cache com Simple/Caffeine).

### 7.3 Cruzamento config→contrato na controller — **P1**
- Template de contrato dentro do módulo `config` (`ConfiguracaoController.java:42-64`) viola o isolamento do Modulith.
- **Solução**: mover endpoints `/contrato/template/**` para o módulo `contrato` (dono legítimo do template); `config` fica apenas com o `ConfigKey` + acesso genérico.

### 7.4 Controller sem DTO e sem validação — **P2**
- Usa `Map` como request/response (`ConfiguracaoController.java:31-37`), sem `@Valid`, sem doc Swagger dos campos.
- **Solução**: `ConfiguracaoRequest`/`ConfiguracaoResponse` com campos tipados.

### 7.5 Duplicidade de nomes de chaves — **P1**
- `DataSeeder` registra `valorUnitarioFotoExtra`/`percentualEntrada` enquanto o `AGENTS.md` e módulos documentam `comissao_percentual_padrao`/`percentual_entrada_padrao`. Consumidores podem ler chaves que não existem (default silencioso).
- **Solução**: centralizar nomes no `ConfigKey` (ver 7.1) e corrigir o `DataSeeder` para usar a mesma nomenclatura.

### 7.6 `getValorTexto` com tamanho ilimitado — **P3**
- `atualizarValorTexto` loga valor truncado mas aceita qualquer tamanho (`ConfiguracaoService.java:69-79`). Para `contratoTemplateTexto`, considerar limite ou versionamento.

## 8. Exemplos de arquivos afetados
- `ConfiguracaoController.java:21-27` — dependência cross-module de `ContratoTemplateService`; `ConfiguracaoService.java:48-52` — conversão sem validação; `ConfiguracaoService.java:34-45` — upsert sem schema; `DataSeeder` (shared) — nomes de chaves divergentes.