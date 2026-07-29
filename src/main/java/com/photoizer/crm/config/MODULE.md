# Módulo: Config

## 1. Responsabilidade
Gerencia configurações globais do sistema no modelo chave-valor. Usado por outros módulos para obter parâmetros como percentual de entrada, taxa de deslocamento, prazos de expiração, etc.

## 2. Estrutura
```
config/
├── model/
│   └── Configuracao.java         # Entidade JPA simples (@Id String chave, String valor)
├── repository/
│   └── ConfiguracaoRepository.java # JpaRepository<String, Configuracao>
├── service/
│   └── ConfiguracaoService.java    # GET all, atualizar múltiplos, getValorDecimal(chave, default)
└── api/
    └── ConfiguracaoController.java # GET /api/v1/config, PUT /api/v1/config
```

## 3. Dependências Externas

### Módulos internos
Nenhum. Módulo fundacional — não importa módulo de negócio.

### Módulos que dependem deste
| Módulo | Uso |
|--------|-----|
| agenda | `ConfiguracaoService.getValorDecimal()` para `percentual_entrada_padrao` e `taxa_deslocamento_padrao` |
| comissao | `comissao_percentual_padrao` (lido no `IndicacaoListener`) |
| notificacao | Prazos de lembrete e alerta |

### Eventos
Nenhum. Módulo não publica nem consome eventos.

## 4. Fluxos Principais

### Fluxo 1: Leitura de Configuração
- `GET /api/v1/config` → retorna `Map<String, Object>` com todas as chaves
- `ConfiguracaoService.getValorDecimal(chave, default)` → busca por chave, converte valor String para `BigDecimal`, retorna default se não encontrado

### Fluxo 2: Atualização de Configuração
- `PUT /api/v1/config` → recebe `Map<String, String>` com pares chave-valor
- `ConfiguracaoService.atualizarMultiplos()`:
  - Para cada entrada: busca por chave ou cria nova `Configuracao`
  - Salva no banco (upsert)

### Chaves Conhecidas (populadas pelo DataSeeder)
| Chave | Descrição | Tipo |
|-------|-----------|------|
| `comissao_percentual_padrao` | Percentual padrão de comissão de indicação | BigDecimal |
| `taxa_deslocamento_padrao` | Taxa de deslocamento para agendamentos | BigDecimal |
| `percentual_entrada_padrao` | Percentual de entrada exigido no agendamento | BigDecimal |
| `prazo_lembrete_ensaio_dias` | Dias antes do ensaio para enviar lembrete | Inteiro |
| `prazo_alerta_edicao_dias` | Dias máximo para conclusão da edição | Inteiro |
| `prazo_expiracao_token_galeria_dias` | Dias de validade do token da galeria | Inteiro |

## 5. Regras Específicas
1. **Modelo chave-valor simples**: Sem estruturação, sem tipos — tudo é armazenado como `String`. A conversão para `BigDecimal` é feita manualmente no service.
2. **Não estende BaseEntity**: `Configuracao` é uma entidade mínima com `@Id String chave` + `String valor`. Não tem `id` UUID, `createdAt`, `updatedAt` ou `createdBy`.
3. **Upsert implícito**: `atualizarMultiplos` cria nova config se a chave não existir. Qualquer string como chave é aceita.
4. **Sem cache**: Toda leitura vai ao banco. Para configurações lidas em alta frequência (percentual_entrada), isso pode ser um gargalo.

## 6. Testes
Nenhum teste específico para este módulo.

## 7. Pontos de Atenção
- **Sem validação de chaves ou valores**: `PUT /api/v1/config` aceita qualquer `Map<String, String>`. Não há schema definido — chaves digitadas erradas criam entradas órfãs.
- **Conversão `String` → `BigDecimal` frágil**: Se o valor armazenado não for um número válido, `getValorDecimal()` lança `NumberFormatException`. Não há tratamento de erro na conversão.
- **Controller sem DTO**: Usa `Map<String, String>` diretamente como `@RequestBody` — sem validação, sem documentação Swagger dos campos esperados.
- **`getValorDecimal` sem transação explícita**: O método não tem `@Transactional(readOnly = true)` — mas transação padrão da classe `@Transactional` cobre. Como tem `readOnly = true` na classe, não há problema, mas é sutil.
- **Chaves hardcoded**: Os nomes das chaves são strings literais espalhadas pelos módulos consumidores. Uma mudança de nome de chave quebra silenciosamente o sistema.
