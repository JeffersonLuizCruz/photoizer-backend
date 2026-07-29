# Módulo: Comissão

## 1. Responsabilidade
Gerencia comissões de indicação. É um módulo puramente **reativo** — não tem endpoints de criação (as comissões nascem de eventos do módulo `agenda`). Fornece endpoints de consulta para indicadores verificarem suas comissões pendentes e pagas.

## 2. Estrutura
```
comissao/
├── model/
│   └── Indicacao.java       # Entidade JPA (extends BaseEntity, @SuperBuilder)
├── repository/
│   └── IndicacaoRepository.java # JpaRepository + consultas por agendamentoId e telefone
├── service/
│   ├── IndicacaoService.java    # Criação, marcar como paga, marcar como cancelada, consulta por telefone
│   └── IndicacaoListener.java   # @EventListener: reage a eventos de agenda
├── api/
│   ├── IndicacaoController.java # GET /consulta?telefone=, GET /indicadores
│   └── IndicacaoResponse.java   # Record com dados da indicação + dados do agendamento
└── exception/
    └── IndicacaoNaoEncontradaException.java # RuntimeException
```

## 3. Dependências Externas

### Módulos internos (importados diretamente — violam Modulith)
- **agenda** → `AgendamentoRepository`, `Agendamento` (usados no controller para enriquecer resposta) — **[VIOLAÇÃO]**
- **agenda (eventos)** → `AgendamentoCriadoEvent`, `AgendamentoCanceladoEvent`, `PagamentoFinalRegistradoEvent` (consumidos pelo listener)
- **indicador** → `IndicadorService` (usado no listener para `buscarOuCriar()`) — **[VIOLAÇÃO]**
- **shared** → `BaseEntity`

### Módulos que dependem deste
Nenhum. Comissão é um módulo terminal — só é consultado externamente.

### Eventos consumidos
| Evento | Ação |
|--------|------|
| `AgendamentoCriadoEvent` | Se `indicadorNome` e `indicadorTelefone` preenchidos, cria nova `Indicacao` com status PENDENTE e percentual do evento (ou 10% default) |
| `PagamentoFinalRegistradoEvent` | Marca todas as indicações PENDENTE do agendamento como PAGA |
| `AgendamentoCanceladoEvent` | Marca todas as indicações PENDENTE do agendamento como CANCELADA |

## 4. Fluxos Principais

### Fluxo 1: Criação de Comissão (via Evento)
1. `AgendamentoCriadoEvent` é publicado pelo módulo `agenda` ao criar um agendamento com indicação
2. `IndicacaoListener.handleAgendamentoCriado()`:
   - Ignora se `indicadorNome` ou `indicadorTelefone` estiverem em branco
   - Define `percentual = event.percentualComissao() ?? 10` (10% default)
   - Chama `IndicadorService.buscarOuCriar()` para obter/criar o indicador
   - Chama `IndicacaoService.criar()` com `status = "PENDENTE"`, `origem = "PACOTE"`
   - Cálculo: `valorComissao = valorReferencia * percentual / 100` (HALF_UP, 2 casas)

### Fluxo 2: Pagamento/Cancelamento de Comissões
- `PagamentoFinalRegistradoEvent` → `handlePagamentoRegistrado()`:
  - Busca todas `Indicacao` do `agendamentoId`
  - Para cada `status == "PENDENTE"`, seta `status = "PAGA"` e `dataPagamento = now`
- `AgendamentoCanceladoEvent` → `handleAgendamentoCancelado()`:
  - Busca todas `Indicacao` do `agendamentoId`
  - Para cada `status == "PENDENTE"`, seta `status = "CANCELADA"`

### Fluxo 3: Consulta de Comissões
- `GET /api/v1/comissoes/consulta?telefone=` → retorna:
  - `indicadorNome`, `indicadorTelefone`
  - `totalPendente`, `totalPago` (somas)
  - `indicacoes` (lista enriquecida com dados do agendamento via `AgendamentoRepository`)
- `GET /api/v1/comissoes/indicadores` → retorna agregado por telefone:
  - Para cada telefone distinto com indicações, calcula `totalPendente`, `totalPago`, `totalCancelado`, `totalIndicacoes`

## 5. Regras Específicas
1. **Módulo puramente reativo**: Não há endpoint de criação manual de comissão. Toda comissão nasce de um evento de agenda com dados de indicação.
2. **`status` como String (não enum)**: Valores esperados: `"PENDENTE"`, `"PAGA"`, `"CANCELADA"`. Qualquer string diferente é aceita e pode causar comportamento inesperado nos listeners.
3. **Controller viola Modulith**: `IndicacaoController` injeta `AgendamentoRepository` diretamente para popular dados do agendamento na resposta. O correto seria o módulo `agenda` expor um DTO ou evento com os dados necessários.
4. **Percentual default 10%**: Se `AgendamentoCriadoEvent.percentualComissao()` for `null`, usa `BigDecimal.TEN`.
5. **`IndicacaoListener` também importa `IndicadorService`**: Dependência direta do módulo `indicador`.

## 6. Testes
Nenhum teste específico para este módulo.

## 7. Pontos de Atenção
- **`listarIndicadores` faz N+1 queries**: Para cada telefone distinto, busca todas as indicações individualmente. Poderia ser uma query agregada no repositório.
- **`origem` como String**: Atualmente só usa `"PACOTE"` (hardcoded), mas o campo aceita qualquer string.
- **`IndicacaoResponse` inclui dados financeiros**: `valorTotalFinal`, `valorExtras` do agendamento — expõe dados sensíveis na consulta pública por telefone.
- **Cálculo financeiro**: `valorReferencia * percentual / 100` com `RoundingMode.HALF_UP` e 2 casas decimais. Se o percentual tiver mais de 2 casas decimais, pode haver perda de precisão.
- **Sem verificação de duplicidade**: Se o mesmo `AgendamentoCriadoEvent` for processado duas vezes (retry, reprocessamento), serão criadas duas comissões para o mesmo agendamento.
