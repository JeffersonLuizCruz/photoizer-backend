# Módulo: Dashboard

## 1. Responsabilidade
Módulo de **consulta e agregação** de métricas para o painel. Não possui entidade/repositório próprios — lê dados de **6 módulos** (agenda, cliente, comissao, despesa, ecommerce, financeiro) para produzir KPIs, séries financeiras mensais e métricas de e-commerce. É o único módulo puramente de **leitura** do sistema.

## 2. Estrutura
```
dashboard/
├── service/
│   └── DashboardService.java       # 394 linhas: calcularFinanceiroMensal, calcularEcommerce, calcularEcommerceMensal, calcularKpis
└── api/
    ├── DashboardController.java    # 4 GETs sob /api/v1/dashboard
    ├── DashboardKpisResponse.java          # agendamentosMes, receitaMes, taxaConversao, novosClientesMes, agendamentosHoje
    ├── DashboardMensalResponse.java        # resumoMesAtual + histórico (DadosMensais)
    ├── DashboardEcommerceResponse.java     # totalCompras, totalFotosExtras, totalFaturado, ticketMedio, topClientes
    └── DashboardEcommerceMensalResponse.java # histórico de vendas mensais (DadosEcommerceMensal)
```

## 3. Dependências Externas

### Módulos internos importados — **[VIOLAÇÕES Modulith]**
| Módulo | Repositórios/Modelos | Uso |
|--------|----------------------|-----|
| **agenda** | `AgendamentoRepository`, `AgendamentoFotografoRepository`, `Agendamento`, `AgendamentoFotografo`, `StatusAgendamento`, `RepasseStatus` | receita, séries mensais, repasses |
| **cliente** | `ClienteRepository` | `countByDataCadastroBetween` |
| **comissao** | `IndicacaoRepository`, `Indicacao` | comissões por agendamento |
| **despesa** | `DespesaRepository`, `Despesa`, `StatusDespesa` | despesas por mês |
| **ecommerce** | `CompraExtraRepository`, `CompraExtra`, `StatusCompraExtra` | vendas extras |
| **financeiro** | `ReceitaRepository`, `Receita`, `StatusReceita` | receitas avulsas |

> Todos os métodos são `@Transactional(readOnly = true)` em nível de classe — bom, mas o acoplamento é por **repositório de outros módulos** (sem facades/queries públicas), o que viola o isolamento Modulith e impede evoluções internas dos módulos donos.

### Módulos que dependem deste
Nenhum (somente o frontend consome via API).

### Eventos
Nenhum (módulo apenas lê).

## 4. Fluxos Principais

### Fluxo 1: KPIs (`GET /api/v1/dashboard/kpis`)
`calcularKpis` (`DashboardService.java:335-368`):
- `agendamentosMes`/`agendamentosHoje` → `countByDataHoraEnsaioBetween` (SQL, ok).
- `receitaMes` → **`agendamentoRepository.findAll()`** + filtro em memória por mês/status + soma `valorTotalFinal` (`:344-355`).
- `novosClientesMes` → `countByDataCadastroBetween`.
- `taxaConversao` → `agendamentosMes / agendamentoRepository.count()`.

### Fluxo 2: Financeiro Mensal (`GET /financeiro-mensal?meses=6`)
`calcularFinanceiroMensal` (`:84-246`):
1. Busca agendamentos no período via `findByDataBetween` (ignora `CANCELADO`/`NO_SHOW`).
2. Comissões: `indicacaoRepository.findByAgendamentoIdIn` + `Map` por agendamento; strings `"CANCELADA"`/`"PAGA"` (`:57-58,105-110`).
3. Despesas: `findByDataBetweenOrderByDataDesc` agrupadas por `YearMonth`.
4. Repasses: `carregarRepasses` (`:375-389`) usa projeção `sumRepassesAtivosPorAgendamento` (SQL agregado) mas lê como `Object[]` (sem tipagem).
5. Receitas avulsas: **`receitaRepository.findAll()`** + filtro `agendamentoId == null` (`:128-130`).
6. Para cada mês calcula confirmados/finalizados, deslocamento, comissão, repasse, despesas, saldo líquido e projeções.

### Fluxo 3: E-commerce
- `GET /ecommerce` → `calcularEcommerce` (`:248-293`): `findByStatus(PAGA)`, agrega totais/ticket médio, `findAllById` nos agendamentos e top 5 clientes por gasto.
- `GET /ecommerce/mensal?meses=6` → `calcularEcommerceMensal` (`:295-333`): **`findAll()`** + filtro por `createdAt` em memória, agrupa por mês.

## 5. Regras Específicas
1. **Sem modelo próprio**: não há `model/`, `repository/` nem `exception/` — apenas leitura.
2. **`STATUS_IGNORADOS`** = `CANCELADO`, `NO_SHOW`; **`STATUS_FINALIZADOS`** = `EM_EDICAO`, `SELECAO_DAS_FOTOS`, `FOTOS_ENVIADAS_PARA_SELECAO`, `FOTOS_ENTREGUES`, `FINALIZADO` (`:45-55`).
3. **Deslocamento efetivo**: `deslocamentoEfetivo` (`:370-373`) retorna **0 quando `repassarDeslocamento=true`** (custo absorvido pelo cliente); idêntico à lógica do financeiro/agenda (duplicação).
4. **Status de comissão via String**: constantes `"CANCELADA"`/`"PAGA"` comparadas com `equals` (mesma dívida do módulo comissao).
5. **Sem cache**: cada request re-executa agregações pesadas.

## 6. Testes
Nenhum teste específico. Apenas `CrmApplicationTests` (smoke de contexto).

## 7. Dívidas Técnicas e Melhorias Recomendadas

### 7.1 `findAll()` + agregação em memória — **[CRÍTICO] P1**
- `receitaMes` (`DashboardService.java:344`), receitas avulsas (`:128`), `calcularEcommerceMensal` (`:301`). Conforme o volume cresce, carregar tudo em memória degrada severamente.
- **Solução**: queries agregadas SQL (`SUM`/`GROUP BY`/`COUNT`) nos repositórios donos; expor **projeções/projeções via facades públicas** dos módulos (com `@EntityGraph`), não repositórios crus.

### 7.2 Acoplamento a 6 módulos por repositório direto — **P1**
- `DashboardService` injeta 7 repositórios de 6 módulos (`:60-66`).
- **Solução**: cada módulo expõe uma API de consulta/query de leitura (ex.: `AgendamentoQueryService.obterReceitaMensal(...)`, `CompraExtraQueryService.vendasMensais(...)`), o dashboard compõe — reduz o acoplamento e centraliza as regras nos donos.

### 7.3 Regras financeiras duplicadas (deslocamento, comissão, repasse) — **P1**
- A lógica de "efetivo/pago" de deslocamento, comissão e repasse (`:151-179,195-215`) repete os cálculos do `FinanceiroService`/`FinanceiroDashboardService` — risco de divergência de números entre telas.
- **Solução**: componente único `FinanceCalculator`/`PartilhaCalculator` (ver financeiro/MODULE.md 7.5) usado por dashboard e financeiro.

### 7.4 Projeção não tipada (`Object[]`) — **P2**
- `carregarRepasses` (`:375-389`) recebe `Object[]` de `sumRepassesAtivosPorAgendamento` (uso de `interface-based projection` ou `Tuple`).
- **Solução**: interface de projeção tipada no `AgendamentoFotografoRepository`.

### 7.5 Sem cache — **P2**
- Consultas pesadas a cada request, com históricos idênticos para o mesmo `meses`.
- **Solução**: cache curto (`@Cacheable`/Caffeine) ou materialização agendada das séries mensais.

### 7.6 Comparações de string de status — **P2**
- `STATUS_COMISSAO_CANCELADA`/`PAGA` (`:57-58`) — deve usar o enum de comissao quando criado.
- `STATUS_IGNORADOS`/`STATUS_FINALIZADOS` duplicam o vocabulário de status do agenda; considerar extrair no domínio da agenda.

### 7.7 `taxaConversao` em `double` e itens sem `sort` — **P3**
- Divisão `agendamentosMes/count` em `double`; `topClientes` sem ordenação por data/valor estável; formatação `yyyy-MM` no service (`:204,327`) em vez de DTO.

### 7.8 Nome do KPI desatualizado no doc anterior — **P3**
- O MODULE.md antigo citava `TarefaRepository`/`tarefasPendentes`/`StatusTarefa`; a implementação atual não os usa (KPI removido). Este documento corrige.

## 8. Exemplos de arquivos afetados
- `DashboardService.java:60-66` — 7 repositórios de 6 módulos; `:128-130,301-305,344-355` — `findAll()` em memória; `:151-179` — regras financeiras duplicadas; `:375-389` — projeção `Object[]`.
- `DashboardController.java:23-47` — 4 endpoints (sem paginação/cache).
- `DashboardKpisResponse.java:5-11` — sem `tarefasPendentes` (doc antigo desatualizado).
- `auth/config/SecurityConfig.java:75` — rota `/api/v1/dashboard/**` com `hasAnyRole(ADMIN, FOTOGRAFO, EDITOR)`.