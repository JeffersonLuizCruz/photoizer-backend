# Módulo: Financeiro

## 1. Responsabilidade
Gerencia cálculos financeiros, pagamentos, fotos extras e vídeos extras. Provê endpoints de preview financeiro (antes da criação do agendamento), resumo financeiro, relatórios detalhados e registro de pagamentos. Também cria comissões de indicação para vendas de extras.

## 2. Estrutura
```
financeiro/
├── model/
│   ├── Pagamento.java     # Entidade JPA (extends BaseEntity): agendamento (ManyToOne), valor, dataPagamento, observacao
│   ├── FotoExtra.java     # Entidade JPA (extends BaseEntity): agendamento (ManyToOne), quantidade, valorUnitario, valorTotal
│   └── VideoExtra.java    # Entidade JPA (extends BaseEntity): agendamento (ManyToOne), quantidade, valorUnitario, valorTotal
├── repository/
│   ├── PagamentoRepository.java   # JpaRepository + findByAgendamentoId
│   ├── FotoExtraRepository.java   # JpaRepository
│   └── VideoExtraRepository.java  # JpaRepository
├── service/
│   └── FinanceiroService.java     # 284 linhas: preview, resumo, relatório, registrar pagamento, adicionar extras, comissão
├── api/
│   ├── FinanceiroController.java          # REST: preview, resumo, relatórios, pagamentos, extras
│   ├── FinanceiroPreviewResponse.java     # Record: valorTotal, valorEntradaExigido, valorRestante, valorTotalFinal, percentualEntrada
│   ├── FinanceiroResumoResponse.java      # Record: totais de entradas, final, extras, faturamento, deslocamento, comissao, despesas
│   └── FinanceiroRelatoriosResponse.java  # Record: totais + lista de AgendamentoResponse
└── listener/
    └── FinanceiroEventListener.java # Loga quando recebe AgendamentoRealizadoEvent (apenas log)
```

## 3. Dependências Externas

### Módulos internos
| Módulo | Uso |
|--------|-----|
| **agenda** | `AgendamentoRepository`, `Agendamento`, `StatusAgendamento`, `AgendamentoRealizadoEvent`, `AgendamentoResponse` |
| **comissao** | `IndicacaoRepository`, `Indicacao` (criação direta de Indicacao para comissões de extras) |
| **config** | `ConfiguracaoService` (percentualEntrada, percentualComissao) |
| **despesa** | `DespesaRepository` (para calcular despesas no resumo financeiro) |
| **indicador** | `IndicadorService` (buscarOuCriar para comissões) |
| **pacote** | `PacoteRepository`, `Pacote` |
| **shared** | `BaseEntity` |

### Eventos consumidos
| Evento | Ação |
|--------|------|
| `AgendamentoRealizadoEvent` | `FinanceiroEventListener` apenas loga o evento |

## 4. Fluxos Principais

### Fluxo 1: Preview Financeiro
`GET /api/v1/financeiro/preview?pacoteId=&taxaDeslocamento=` → `calcularPreview()`:
- Busca pacote, obtém `percentualEntrada` do `ConfiguracaoService`
- Calcula: `valorTotal = pacote.valorBase + taxa`, `valorEntradaExigido = pacote.valorBase * (percentualEntrada/100)`, `valorRestante = valorTotal - valorEntradaExigido`

### Fluxo 2: Resumo Financeiro
`GET /api/v1/financeiro/resumo?dataInicio=&dataFim=` → `calcularResumo()`:
- Filtra agendamentos não-cancelados no período
- Soma entradas, valor final, extras, faturamento total, deslocamento
- Busca comissões via `findByAgendamentoIdIn()`
- Busca despesas manuais do período
- Retorna totais agregados

### Fluxo 3: Registro de Pagamento
`POST /api/v1/financeiro/pagamentos` → `registrarPagamento()`:
- Adiciona valor ao `valorEntradaPago` do agendamento
- Recalcula `valorRestante`
- Se `valorRestante <= 0`, muda status para `AGUARDANDO_PAGAMENTO_FINAL`
- Salva `Pagamento` vinculado ao agendamento

### Fluxo 4: Adicionar Extras (com comissão)
- `POST /api/v1/financeiro/fotos-extras` ou `/videos-extras` → `adicionarFotoExtra()` / `adicionarVideoExtra()`:
  - Calcula `valorTotal = quantidade * valorUnitario`
  - Atualiza `valorExtras` e `valorTotalFinal` no agendamento
  - Se informado indicador: cria `Indicacao` com origem `FOTO_EXTRA` ou `VIDEO_EXTRA`, percentual do `ConfiguracaoService`

### Fluxo 5: Relatório Detalhado
`GET /api/v1/financeiro/relatorios?dataInicio=&dataFim=` → `calcularRelatorios()`:
- Similar ao resumo, mas retorna lista completa de agendamentos com totais por agendamento

## 5. Regras Específicas
1. **Criação direta de `Indicacao`**: `FinanceiroService.criarComissaoSeNecessario()` cria `Indicacao` diretamente via `IndicacaoRepository` — **viola isolamento do módulo `comissao`** (deveria publicar evento).
2. **Percentuais lidos do Config**: `percentualEntrada` (chave `percentualEntrada` — sem `_padrao`) e `percentualComissao` (chave `percentualComissao` — diferente da usada no `IndicacaoListener` que é `comissao_percentual_padrao`). **[REVISAR HUMANO]**
3. **Status ignorados**: `CANCELADO` e `NO_SHOW` são excluídos de todos os cálculos.
4. **Status considerados "pagamento final"**: `EM_EDICAO`, `FOTOS_ENVIADAS_PARA_SELECAO`, `FOTOS_ENTREGUES`, `FINALIZADO`.
5. **FinanceiroEventListener vazio**: Apenas loga o `AgendamentoRealizadoEvent` — nenhuma ação real.

## 6. Testes
Nenhum teste específico para este módulo.

## 7. Pontos de Atenção
- **Criação direta de `Indicacao`**: `FinanceiroService` salva `Indicacao` diretamente no banco em vez de publicar um evento. Isso duplica a lógica de criação de comissão que também existe no `IndicacaoListener` (módulo `comissao`).
- **Chave de config divergente**: `FinanceiroService` usa `percentualComissao` enquanto `IndicacaoListener` (módulo `comissao`) usa `comissao_percentual_padrao` — duas chaves diferentes para o mesmo conceito.
- **`calcularResumo` vs `calcularRelatorios`**: Lógica similar duplicada — ambos filtram agendamentos, somam valores. Diferença: um retorna agregado, outro retorna lista.
- **`registrarPagamento` modifica status do agendamento**: Efeito colateral no módulo `agenda` — muda o status diretamente via `AgendamentoRepository`.
- **`isClienteBloqueado` faz `findAll()`**: Carrega todos os agendamentos em memória para verificar se cliente tem débitos — ineficiente.
