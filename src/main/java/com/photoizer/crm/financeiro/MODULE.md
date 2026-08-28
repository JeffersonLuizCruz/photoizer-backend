# Módulo: Financeiro

## 1. Responsabilidade
Centraliza **cálculos financeiros do negócio**: preview de valores, resumo, relatórios (mensal, categorias, inadimplência, rentabilidade, comparativo, fiscal), fluxo de caixa, dashboard financeiro, registro de pagamentos e venda de fotos/vídeos extras (com comissão). Também cria comissões de indicação para extras e possui reconciliador de compras extras do e-commerce.

## 2. Estrutura
```
financeiro/
├── model/
│   ├── Pagamento.java           # Entidade: pagamento vinculado a agendamento
│   ├── ExtraServico.java        # Entidade unificada (FotoExtra + VideoExtra) com discriminador TipoExtra
│   ├── TipoExtra.java           # Enum: FOTO, VIDEO
│   ├── Receita.java             # Entidade: receita avulsa
│   ├── StatusReceita.java       # Enum: PENDENTE, PAGO_PARCIAL, PAGO_TOTAL, CANCELADO
│   └── TipoServico.java         # Enum com label(): ENSAIO, CASAMENTO, EVENTO, PRODUTO, OUTRO
├── repository/
│   ├── PagamentoRepository.java       # JpaRepository + findByAgendamentoId, findByCompraExtraId
│   ├── ExtraServicoRepository.java    # JpaRepository + findByAgendamentoId, findByAgendamentoIdAndTipo
│   └── ReceitaRepository.java         # JpaRepository + JpaSpecificationExecutor + queries de inadimplência/relatórios
├── service/
│   ├── PagamentoService.java              # Registro de pagamentos + publica PagamentoRegistradoEvent
│   ├── ExtraVendaService.java             # Venda de fotos/vídeos extras + publica eventos
│   ├── FinanceiroQueryService.java        # Queries de leitura: preview, resumo, relatórios, fluxo de caixa, bloqueio
│   ├── FinanceiroDashboardService.java    # Dashboard agregado (delega repasses ao FinanceCalculator)
│   ├── FinanceiroRelatorioService.java    # Relatórios para exportação (queries SQL)
│   ├── FinanceiroService.java             # Orchestrator fino (delega para services especializados)
│   ├── ReceitaService.java                # CRUD de receitas avulsas
│   └── ReceitaQueryService.java           # Facade de leitura para dashboard
├── api/
│   ├── FinanceiroController.java          # Pagamentos, extras, resumo, dashboard, fluxo, relatórios, bloqueio
│   ├── FinanceiroRelatorioController.java # Relatórios para exportação
│   ├── ReceitaController.java             # CRUD de receitas avulsas
│   ├── ExtraServicoMapper.java            # MapStruct mapper para ExtraServico
│   ├── ExtraServicoResponse.java          # DTO de response para extras
│   ├── PagamentoResponse.java             # DTO de response para pagamentos
│   ├── ReceitaResponse.java               # DTO de response para receitas
│   ├── RelatorioAgendamentoItem.java      # DTO próprio (anti-corruption layer vs agenda)
│   └── ~14 records de Response/preview
├── event/
│   ├── PagamentoRegistradoEvent.java      # Domain Event — publicado ao registrar pagamento
│   └── ExtrasAdicionadosEvent.java        # Domain Event — publicado ao adicionar extras
├── listener/
│   └── FinanceiroEventListener.java       # Consome AgendamentoRealizadoEvent + CompraExtraConfirmadaEvent
├── exception/
│   ├── PagamentoNaoEncontradoException.java
│   ├── AgendamentoNaoEncontradoParaFinanceiroException.java
│   └── ValorInvalidoException.java
└── ReconciliarComprasExtraFinanceiro.java # CommandLineRunner: reconcilia CompraExtra PAGA sem Pagamento no boot
```

## 3. Dependências Externas

### Módulos internos importados
| Módulo | Uso | Tipo |
|--------|-----|------|
| **agenda** | `Agendamento`, `AgendamentoRepository`, `AgendamentoFotografoRepository`, `StatusAgendamento`, `RepasseStatus` | leitura (via FinanceiroQueryService) |
| **comissao** | `Indicacao`, `IndicacaoRepository`, `StatusIndicacao`, `ComissaoSolicitadaEvent` | leitura + publicação de evento |
| **config** | `ConfiguracaoService` (`percentualEntrada`, `percentualComissao`) | leitura |
| **despesa** | `Despesa`, `DespesaRepository`, `DespesaMapper`, `StatusDespesa` | leitura |
| **pacote** | `Pacote`, `PacoteRepository` | leitura |
| **cliente** | `ClienteRepository` (ReceitaService) | leitura |
| **ecommerce** | `CompraExtraRepository` (Reconciliar), recebe `CompraExtraConfirmadaEvent` | eventos + repo |
| **shared** | `AuditInfo`, `FormaPagamento`, `TipoRepasse`, `FinanceCalculator` | infraestrutura + cálculos |

### Eventos consumidos
| Evento | Ação |
|--------|------|
| `agenda.AgendamentoRealizadoEvent` | `FinanceiroEventListener.handleAgendamentoRealizado` — log |
| `ecommerce.CompraExtraConfirmadaEvent` | `PagamentoService.registrarPagamentoExtraEcommerce` — contabiliza extras |

### Eventos publicados
| Evento | Ação |
|--------|------|
| `PagamentoRegistradoEvent` | Consumido por `agenda.PagamentoFinanceiroEventListener` — atualiza status/valores do Agendamento |
| `ExtrasAdicionadosEvent` | Consumido por `agenda.PagamentoFinanceiroEventListener` — atualiza valorExtras/valorTotalFinal |
| `ComissaoSolicitadaEvent` | Consumido por `comissao.IndicacaoListener` — cria Indicacao |

## 4. Fluxos Principais

### Fluxo 1: Preview / Resumo / Relatórios
1. `POST /financeiro/preview` → `FinanceiroQueryService.calcularPreview()`: calcula entrada exigida (30% do total por default), restante e total final.
2. `GET /financeiro/resumo` → `FinanceiroQueryService.calcularResumo()`: usa queries SQL para somar entradas/finais/extras/deslocamento/repasse, comissões e despesas.
3. `GET /financeiro/relatorios` → `FinanceiroQueryService.calcularRelatorios()`: retorna totais + lista de `RelatorioAgendamentoItem`.

### Fluxo 2: Dashboard Financeiro
1. `GET /financeiro/dashboard` → `FinanceiroDashboardService.calcular()`: carrega dados via Specification (receitas), queries (despesas/agendamentos/indicações), e `FinanceCalculator.carregarRepasses()`. Calcula cards, barra mensal, despesas por categoria, lucro mensal, rentabilidade por serviço/trabalho, últimos lançamentos.

### Fluxo 3: Registro de Pagamento
`POST /financeiro/agendamentos/{id}/pagamentos` → `PagamentoService.registrarPagamento()`:
1. Salva `Pagamento`.
2. Publica `PagamentoRegistradoEvent`.
3. `agenda.PagamentoFinanceiroEventListener` consome o evento: chama `agendamento.registrarPagamento(valor)` (domain method) que atualiza `valorEntradaPago`, `valorRestante` e possivelmente `status`.

### Fluxo 4: Extras com Comissão
`POST /agendamentos/{id}/fotos-extras` / `videos-extras` → `ExtraVendaService.adicionarFotoExtra/VideoExtra()`:
1. Salva `ExtraServico` (entidade unificada).
2. Publica `ExtrasAdicionadosEvent` → agenda consome e chama `agendamento.adicionarExtras(valor)`.
3. Publica `ComissaoSolicitadaEvent` → comissao consome e cria `Indicacao`.

### Fluxo 5: Receitas Avulsas
`ReceitaService`: CRUD de receitas manuais — deriva status de valor recebido, calcula comissão (`percentualComissao` default 10%) e `valorFinal`.

### Fluxo 6: Reconciliador
`ReconciliarComprasExtraFinanceiro.run`: no boot, busca `CompraExtra PAGA` sem `Pagamento` correspondente e chama `PagamentoService.registrarPagamentoExtraEcommerce()`.

## 5. Regras Específicas
1. **Status ignorados** em todos os cálculos: `CANCELADO`, `NO_SHOW`; "pagamento final" considerado para `EM_EDICAO`, `FOTOS_ENVIADAS_PARA_SELECAO`, `FOTOS_ENTREGUES`, `FINALIZADO`.
2. **Comissão de extras** via evento `ComissaoSolicitadaEvent` — nunca cria `Indicacao` diretamente.
3. **`isClienteBloqueado`**: usa query SQL `existsByClienteIdWithSaldoDevedor()` (O(1)).
4. **`labelServico`**: centralizado no enum `TipoServico.label()` (DRY).
5. **`emPeriodo`**: centralizado em `FinanceiroQueryService.emPeriodo()` (package-private, usado por Dashboard e Relatorio).
6. **Repasses**: delegados a `FinanceCalculator.carregarRepasses()` (shared).

## 6. Testes
Nenhum teste específico para este módulo. Apenas `CrmApplicationTests` (smoke de contexto).

## 7. Dívidas Técnicas Resolvidas (Fase 3)

| Item | Status | Descrição |
|------|--------|-----------|
| 7.1 God classes (600+608 linhas) | **RESOLVIDO** | `FinanceiroService` extraído em `PagamentoService`, `ExtraVendaService`, `FinanceiroQueryService`. `FinanceiroDashboardService` delega repasses ao `FinanceCalculator`. |
| 7.2 `findAll()` + streams | **RESOLVIDO** | `isClienteBloqueado` usa query SQL; `FinanceiroRelatorioService` usa `findInadimplentes()`, `findAvulsasByDataBetween()`, `sumValorByDataBetween()`; queries SQL em `DespesaRepository` e `IndicacaoRepository`. |
| 7.3 Escrita cross-module | **RESOLVIDO** | `PagamentoRegistradoEvent` + `ExtrasAdicionadosEvent` eliminam mutação direta no `Agendamento`. Listener no agenda consome eventos. |
| 7.4 Exposição de entidades | **RESOLVIDO** | `FinanceiroController` retorna `PagamentoResponse`, `ExtraServicoResponse` em vez de entidades. |
| 7.5 Duplicação de regra financeira | **RESOLVIDO** | `FinanceCalculator` (shared) centraliza `deslocamentoEfetivo()` e `carregarRepasses()`. |
| 7.7 Exceções genéricas | **PARCIAL** | Criadas `PagamentoNaoEncontradoException`, `AgendamentoNaoEncontradoParaFinanceiroException`, `ValorInvalidoException`. |
| 7.12 Bug: `dataPagamento` sobrescrita | **RESOLVIDO** | `PagamentoService.registrarPagamento()` agora preserva `dataPagamento` do request body (antes sempre setava `now()`). |
| 7.8 DTOs cross-module | **RESOLVIDO** | `RelatorioAgendamentoItem` substitui `AgendamentoResponse` do agenda. |
| 7.9 Duplicidades internas | **RESOLVIDO** | `labelServico()` → `TipoServico.label()`; `emPeriodo()` → `FinanceiroQueryService.emPeriodo()`; repasses → `FinanceCalculator`. |
| 7.11 DTOs manuais | **RESOLVIDO** | `ExtraServicoMapper` (MapStruct) criado; `ReceitaResponse.of()` e `PagamentoResponse.of()` mantidos para backward compat. |
| Unificação entidades | **RESOLVIDO** | `FotoExtra` + `VideoExtra` → `ExtraServico` com `TipoExtra` enum. |
