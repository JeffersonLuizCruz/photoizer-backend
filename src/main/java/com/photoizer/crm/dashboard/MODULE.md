# Módulo: Dashboard

## 1. Responsabilidade
Módulo de **consulta e agregação** de métricas para o painel. Não possui entidade/repositório próprios — compõe dados de **6 módulos** via facades de query para produzir KPIs, séries financeiras mensais e métricas de e-commerce. É o único módulo puramente de **leitura** do sistema.

## 2. Estrutura
```
dashboard/
├── service/
│   └── DashboardService.java       # Orchestrator (~230 linhas): compõe dados das facades
└── api/
    ├── DashboardController.java    # 4 GETs sob /api/v1/dashboard
    ├── DashboardKpisResponse.java          # agendamentosMes, receitaMes, taxaConversao, novosClientesMes, agendamentosHoje
    ├── DashboardMensalResponse.java        # resumoMesAtual + histórico (DadosMensais)
    ├── DashboardEcommerceResponse.java     # totalCompras, totalFotosExtras, totalFaturado, ticketMedio, topClientes
    └── DashboardEcommerceMensalResponse.java # histórico de vendas mensais (DadosEcommerceMensal)
```

## 3. Dependências Externas

### Facades de Query (consultas cross-module) — Refatorado
| Módulo | Facade | Uso |
|--------|--------|-----|
| **agenda** | `AgendamentoQueryService` | agendamentos por período, contagem, receita, repasses |
| **comissao** | `ComissaoQueryService` | comissão total/paga por agendamento |
| **despesa** | `DespesaQueryService` | despesas totais/pagas por mês |
| **financeiro** | `ReceitaQueryService` | receitas avulsas por mês |
| **ecommerce** | `EcommerceQueryService` | consolidado de vendas e histórico mensal |
| **cliente** | `ClienteQueryService` | contagem de novos clientes |

### Componentes compartilhados
| Componente | Pacote | Uso |
|------------|--------|-----|
| `FinanceCalculator` | `shared.service` | deslocamentoEfetivo, carregarRepasses, constantes de status |

> **Arquitetura:** O dashboard não acessa repositórios de outros módulos diretamente. Usa facades que encapsulam as queries e regras de negócio no módulo dono. Segue o padrão **Orchestrator** — compõe dados de múltiplas fontes sem possuir estado próprio.

### Módulos que dependem deste
Nenhum (somente o frontend consome via API).

### Eventos
Nenhum (módulo apenas lê).

## 4. Fluxos Principais

### Fluxo 1: KPIs (`GET /api/v1/dashboard/kpis`)
`calcularKpis` com `@Cacheable("dashboard-kpis")`:
- `agendamentosMes`/`agendamentosHoje` → `AgendamentoQueryService.countPorPeriodo()` (SQL).
- `receitaMes` → `AgendamentoQueryService.calcularReceitaPeriodo()` (SQL + filtro de status).
- `novosClientesMes` → `ClienteQueryService.countNovosClientes()`.
- `taxaConversao` → `agendamentosMes / AgendamentoQueryService.countTotal()`.

### Fluxo 2: Financeiro Mensal (`GET /financeiro-mensal?meses=6`)
`calcularFinanceiroMensal` com `@Cacheable("dashboard-financeiro")`:
1. `AgendamentoQueryService.obterPorPeriodo()` — agendamentos ativos no período.
2. `ComissaoQueryService.obterComissaoPorAgendamentos()` — comissão total/paga por agendamento.
3. `DespesaQueryService.obterPorPeriodo()` — despesas agrupadas por mês.
4. `FinanceCalculator.carregarRepasses()` — repasses previstos/pagos por agendamento.
5. `ReceitaQueryService.obterAvulsasPorPeriodo()` — receitas avulsas por mês.
6. Para cada mês calcula confirmados/finalizados, deslocamento, comissão, repasse, despesas, saldo líquido e projeções.

### Fluxo 3: E-commerce
- `GET /ecommerce` → `@Cacheable("dashboard-ecommerce")`: `EcommerceQueryService.obterConsolidado()` + top 5 clientes.
- `GET /ecommerce/mensal?meses=6` → `@Cacheable("dashboard-ecommerce-mensal")`: `EcommerceQueryService.obterHistoricoMensal()`.

## 5. Regras Específicas
1. **Sem modelo próprio**: não há `model/`, `repository/` nem `exception/` — apenas leitura via facades.
2. **FinanceCalculator** centraliza: `deslocamentoEfetivo()`, `carregarRepasses()`, constantes `STATUS_IGNORADOS`/`STATUS_FINALIZADOS`/`STATUS_CONFIRMADOS_OU_FINALIZADOS`.
3. **Cache**: todos os endpoints usam `@Cacheable` (Spring Cache) para evitar re-execução de agregações pesadas.
4. **Projeção tipada**: `RepasseAggregation` substitui `Object[]` de `sumRepassesAtivosPorAgendamento`.

## 6. Testes
- `DashboardServiceTest` — 6 testes (financeiroMensal: deslocamento, comissão cancelada/paga, avulsa prevista/recebida, despesa pendente).
- Testes rodam isoladamente com mocks das 6 facades + FinanceCalculator real.

## 7. Dívidas Resolvidas (Refatoração)

### ✅ `findAll()` + agregação em memória — **RESOLVIDO**
- `receitaMes`, receitas avulsas, `calcularEcommerceMensal` agora usam queries agregadas via facades.
- `EcommerceQueryService` usa `findByPeriodo()` (JPQL) em vez de `findAll()`.

### ✅ Acoplamento a 6 módulos por repositório direto — **RESOLVIDO**
- `DashboardService` injeta 6 facades + `FinanceCalculator` (zero repositórios).
- Cada módulo expõe API de consulta controlada via `*QueryService`.

### ✅ Regras financeiras duplicadas — **RESOLVIDO**
- `deslocamentoEfetivo()`, `carregarRepasses()`, constantes de status → `FinanceCalculator` (compartilhado com `FinanceiroDashboardService`).

### ✅ Projeção não tipada (`Object[]`) — **RESOLVIDO**
- `RepasseAggregation` interface tipada no `AgendamentoFotografoRepository`.

### ✅ Sem cache — **RESOLVIDO**
- `@Cacheable` em todos os 4 métodos do `DashboardService`.

### ◐ Comparações de string de status — **PARCIALMENTE RESOLVIDO**
- Comissão agora usa `StatusIndicacao` enum diretamente (via `ComissaoQueryService`).
- Status do agenda continuam como enum (via `FinanceCalculator`).

## 8. Exemplos de arquivos afetados
- `DashboardService.java` — reescrito (394→~230 linhas): 6 facades + FinanceCalculator + @Cacheable.
- `DashboardServiceTest.java` — mocks migrados de repos para facades.
- `AgendamentoFotografoRepository.java` — `sumRepassesAtivosPorAgendamento` retorna `List<RepasseAggregation>` (antes `List<Object[]>`).
- `FinanceiroDashboardService.java` — `carregarRepasses()` atualizado para typed projection.
- `FinanceiroService.java` — `repassesPrevistosPorEnsaio()` atualizado para typed projection.
- `FinanceiroServiceTest.java` — correção de bug pré-existente (constructor mismatch).
- Novos arquivos: `FinanceCalculator`, `AgendamentoQueryService`, `ComissaoQueryService`, `DespesaQueryService`, `ReceitaQueryService`, `EcommerceQueryService`, `ClienteQueryService`, `RepasseAggregation`.
