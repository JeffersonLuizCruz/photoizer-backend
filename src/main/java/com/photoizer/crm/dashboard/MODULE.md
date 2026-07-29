# Módulo: Dashboard

## 1. Responsabilidade
Módulo de consulta e agregação de métricas. Não possui entidade própria — consulta dados de 5 módulos diferentes para gerar KPIs, gráficos financeiros mensais e métricas de e-commerce. É o único módulo puramente de **leitura** do sistema.

## 2. Estrutura
```
dashboard/
├── service/
│   └── DashboardService.java        # 315 linhas: 4 métodos de agregação
└── api/
    ├── DashboardController.java     # GET /api/v1/dashboard (4 endpoints)
    ├── DashboardKpisResponse.java    # Record: agendamentosMes, receitaMes, taxaConversao, novosClientesMes, tarefasPendentes, agendamentosHoje
    ├── DashboardMensalResponse.java  # Record: resumoMesAtual + histórico mensal (DadosMensais)
    ├── DashboardEcommerceResponse.java # Record: totalCompras, totalFotosExtras, totalFaturado, ticketMedio, topClientes
    └── DashboardEcommerceMensalResponse.java # Record: histórico de vendas mensais (DadosEcommerceMensal)
```

## 3. Dependências Externas

### Módulos internos (importados diretamente)
| Módulo | Repositórios Importados |
|--------|------------------------|
| **agenda** | `AgendamentoRepository`, `TarefaRepository` |
| **cliente** | `ClienteRepository` |
| **comissao** | `IndicacaoRepository` |
| **despesa** | `DespesaRepository` |
| **ecommerce** | `CompraExtraRepository` |

### Dependências de modelo
- `StatusAgendamento`, `StatusTarefa` (agenda)
- `Indicacao` (comissao)
- `StatusCompraExtra`, `CompraExtra` (ecommerce)

### Eventos
Nenhum. Módulo apenas lê dados.

## 4. Fluxos Principais

### Fluxo 1: KPIs do Dashboard
`GET /api/v1/dashboard/kpis` → `DashboardService.calcularKpis()`:
- **Agendamentos no mês**: `countByDataHoraEnsaioBetween(inicioMes, fimMes)`
- **Agendamentos hoje**: `countByDataHoraEnsaioBetween(hoje.inicio, hoje.fim)`
- **Receita do mês**: `findAll()` + filtro em memória por status + soma de `valorTotalFinal`
- **Novos clientes no mês**: `countByDataCadastroBetween(inicioMes, fimMes)`
- **Tarefas pendentes**: `findAll()` + filtro em memória por `StatusTarefa.PENDENTE`
- **Taxa de conversão**: `agendamentosMes / totalAgendamentos`

### Fluxo 2: Financeiro Mensal
`GET /api/v1/dashboard/financeiro-mensal?meses=6` → `calcularFinanceiroMensal()`:
1. Busca agendamentos no período ignorando `CANCELADO` e `NO_SHOW`
2. Agrupa por `YearMonth` da `dataHoraEnsaio`
3. Para cada mês: soma `valorTotalFinal`, `taxaDeslocamento`, `valorEntradaPago`, comissões e despesas
4. Calcula indicadores: saldo líquido, receita projetada, liquido previsto
5. Retorna histórico mensal + resumo do mês atual

### Fluxo 3: E-commerce
- `GET /api/v1/dashboard/ecommerce` → `calcularEcommerce()`:
  - Filtra `CompraExtra` com `StatusCompraExtra.PAGA` (via repositório)
  - Calcula total faturado, total fotos, total compras, ticket médio
  - Busca agendamentos via `findAllById()` + agrupa por cliente → top 5 por gasto
- `GET /api/v1/dashboard/ecommerce/mensal?meses=6` → `calcularEcommerceMensal()`:
  - Filtra todas compras por data em memória (`findAll()` + stream filter)
  - Agrupa por mês e soma status PAGA

## 5. Regras Específicas
1. **Módulo sem modelo próprio**: Não tem `model/`, `repository/` ou `exception/`. Depende exclusivamente de dados de outros módulos.
2. **Filtros em memória**: `calcularKpis()` faz `findAll()` e filtra em memória para receita e tarefas pendentes. `calcularEcommerceMensal()` também filtra em memória.
3. **`StatusAgendamento` ignorados**: `CANCELADO` e `NO_SHOW` são excluídos de todos os cálculos financeiros.
4. **`StatusAgendamento` considerados finalizados**: `EM_EDICAO`, `SELECAO_DAS_FOTOS`, `FOTOS_ENVIADAS_PARA_SELECAO`, `FOTOS_ENTREGUES`, `FINALIZADO`.
5. **Percentual de comissão hardcoded**: `PERCENTUAL_COMISSAO_PADRAO = BigDecimal.TEN` (não lê de `ConfiguracaoService`).
6. **Módulo de leitura**: Todos os métodos são `@Transactional(readOnly = true)` em nível de classe.

## 6. Testes
Nenhum teste específico para este módulo.

## 7. Pontos de Atenção
- **`calcularKpis()` faz `findAll()` em tabelas grandes**: Para `agendamentoRepository.findAll()` e `tarefaRepository.findAll()`, conforme o volume crescer, o carregamento em memória será ineficiente. Idealmente, usar queries agregadas no banco.
- **`calcularEcommerceMensal()` filtra em memória**: `compraExtraRepository.findAll()` + stream filter. Para muitos registros, isso será lento.
- **`calcularFinanceiroMensal()` repete cálculo de despesas**: Para cada mês, as despesas são buscadas apenas uma vez (fora do loop) mas o cálculo de deslocamento + comissão + despesas manuais é refeito.
- **`TopCliente` usa `agendamentoRepository.findAllById()`**: Depois itera sobre a lista em vez de usar um Map indexado — já faz isso corretamente, mas o `findAllById` pode ordernar por ID, não por data.
- **Sem cache**: Toda requisição ao dashboard faz consultas pesadas ao banco. Para um dashboard real, seria ideal cache ou projeções materializadas.
