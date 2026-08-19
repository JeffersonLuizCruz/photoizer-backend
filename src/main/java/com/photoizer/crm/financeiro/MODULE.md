# Módulo: Financeiro

## 1. Responsabilidade
Centraliza **cálculos financeiros do negócio**: preview de valores, resumo, relatórios (mensal, categorias, inadimplência, rentabilidade, comparativo, fiscal), fluxo de caixa, dashboard financeiro, registro de pagamentos e venda de fotos/vídeos extras (com comissão). Também cria comissões de indicação para extras e possui reconciliador de compras extras do e-commerce.

## 2. Estrutura
```
financeiro/
├── model/
│   ├── Pagamento.java     # Entidade (extends BaseEntity): agendamento (@ManyToOne), valor, dataPagamento, compraExtraId, observacao
│   ├── FotoExtra.java     # Entidade (extends BaseEntity): agendamento (@ManyToOne), quantidade, valorUnitario, valorTotal
│   ├── VideoExtra.java    # Entidade (extends BaseEntity): agendamento (@ManyToOne), quantidade, valorUnitario, valorTotal
│   ├── Receita.java       # Entidade (extends BaseEntity): receita avulsa (clienteId+clienteNome, tipoServico, valores, status, datas, comissão)
│   ├── StatusReceita.java # Enum: PENDENTE, PAGO_PARCIAL, PAGO_TOTAL, CANCELADO
│   └── TipoServico.java   # Enum: ENSAIO, CASAMENTO, EVENTO, PRODUTO, OUTRO
├── repository/
│   ├── PagamentoRepository.java       # JpaRepository + findByAgendamentoId, findByCompraExtraId
│   ├── FotoExtraRepository.java       # JpaRepository
│   ├── VideoExtraRepository.java      # JpaRepository
│   └── ReceitaRepository.java         # JpaRepository + JpaSpecificationExecutor + somas
├── service/
│   ├── FinanceiroService.java           # 600 linhas: preview, resumo, relatórios, pagamentos, extras, comissões, fluxo de caixa, bloqueio de cliente
│   ├── FinanceiroDashboardService.java  # 608 linhas: dashboard agregado (cards, mensal, categorias, lucro, rentabilidade, lançamentos)
│   ├── FinanceiroRelatorioService.java  # 289 linhas: relatórios para exportação
│   └── ReceitaService.java              # 199 linhas: CRUD de receitas avulsas c/ comissão
├── api/
│   ├── FinanceiroController.java        # 148 linhas: pagamentos, extras, resumo, dashboard, fluxo de caixa, relatórios, bloqueio
│   ├── FinanceiroRelatorioController.java # 82 linhas: resumo-mensal, despesas-categoria, inadimplência, rentabilidade, comparativo, fiscal
│   ├── ReceitaController.java           # CRUD de receitas avulsas + receber + duplicar
│   └── ~16 records de Response/preview
├── listener/
│   └── FinanceiroEventListener.java     # Consome AgendamentoRealizadoEvent (log) e CompraExtraConfirmadaEvent (contabiliza)
└── ReconciliarComprasExtraFinanceiro.java # CommandLineRunner: reconcilia CompraExtra PAGA sem Pagamento no boot
```

## 3. Dependências Externas

### Módulos internos importados — **[VIOLAÇÕES Modulith]**
| Módulo | Uso | Tipo |
|--------|-----|------|
| **agenda** | `Agendamento`, `AgendamentoRepository`, `AgendamentoFotografoRepository`, `StatusAgendamento`, `RepasseStatus`, `AgendamentoResponse` | leitura **e escrita** |
| **comissao** | `Indicacao`, `IndicacaoRepository` — **`FinanceiroService` cria `Indicacao` diretamente** | entrada **e escrita** |
| **config** | `ConfiguracaoService` (`percentualEntrada`, `percentualComissao`) | leitura |
| **despesa** | `Despesa`, `DespesaRepository`, `DespesaResponse`, `StatusDespesa` | leitura |
| **indicador** | `IndicadorService` (`buscarOuCriar`/`buscarPorId` p/ comissão de extras) | leitura |
| **pacote** | `Pacote`, `PacoteRepository` | leitura |
| **cliente** | `ClienteRepository` (ReceitaService) | leitura |
| **ecommerce** | `CompraExtraRepository` (Reconciliar), recebe `CompraExtraCriadaEvent`/`CompraExtraConfirmadaEvent` | eventos + repo |
| **shared** | `BaseEntity`, `FormaPagamento`, `TipoRepasse` | infraestrutura |

> **Padrão correto em uso**: `FinanceiroEventListener` consome eventos da agenda e ecommerce — é o caminho certo. As violações estão no service (escritas diretas).

### Eventos consumidos
| Evento | Ação |
|--------|------|
| `agenda.AgendamentoRealizadoEvent` | `FinanceiroEventListener.handleAgendamentoRealizado` — **apenas log** (nenhuma ação financeira) |
| `ecommerce.CompraExtraConfirmadaEvent` | `finaceiroService.registrarPagamentoExtraEcommerce` — contabiliza extras no agendamento |

### Eventos publicados
Nenhum.

## 4. Fluxos Principais

### Fluxo 1: Preview / Resumo / Relatórios
1. `POST /financeiro/preview` → `calcularPreview` (`FinanceiroService.java:94-107`): calcula entrada exigida (30% do total por default), restante e total final.
2. `GET /financeiro/resumo` → `calcularResumo` (`:110-168`): percorre agendamentos não-cancelados, soma entradas/finais/extras/deslocamento/repasse; soma comissões (`indicacaoRepository.findByAgendamentoIdIn`) e despesas do período.
3. `GET /financeiro/relatorios` → `calcularRelatorios` (`:171-208`): mesmo filtro, retorna totais + lista de `AgendamentoResponse`.

### Fluxo 2: Dashboard Financeiro
1. `GET /financeiro/dashboard` → `FinanceiroDashboardService.calcular` (`FinanceiroDashboardService.java:69-98`): **carrega tudo em memória** — `despesaRepository.findAll()`, `agendamentoRepository.findAll()` filtrado, `indicacaoRepository.findAll()`, repsasses agregados — e calcula cards, barra mensal, despesas por categoria, lucro mensal, rentabilidade por serviço/trabalho, últimos lançamentos.

### Fluxo 3: Registro de Pagamento
`POST /financeiro/agendamentos/{id}/pagamentos` → `registrarPagamento` (`FinanceiroService.java:469-483`):
1. **Mutação cross-module**: soma em `agendamento.valorEntradaPago`, recalcula `valorRestante`, e **muda `agendamento.status` para `AGUARDANDO_PAGAMENTO_FINAL`** quando quita.
2. Salva `Pagamento`.

### Fluxo 4: Extras com Comissão
`POST /agendamentos/{id}/fotos-extras` / `videos-extras` → `adicionarFotoExtra`/`adicionarVideoExtra` (`:505-547`):
1. Calcula `valorTotal = qtd × unit`, atualiza `valorExtras`/`valorTotalFinal` do agendamento.
2. `criarComissaoSeNecessario` (`:549-586`): cria `Indicacao` **diretamente** (via `IndicacaoRepository`), com `status "PENDENTE"` em **String**, percentual do indicador ou config `percentualComissao`.
3. Consumido via listener: `CompraExtraConfirmadaEvent` → `registrarPagamentoExtraEcommerce` (`:485-503`) atualiza agendamento + cria `Pagamento` de extras.

### Fluxo 5: Receitas Avulsas
`ReceitaService` (`:40-199`): CRUD de receitas manuais — deriva status de valor recebido, calcula comissão (`percentualComissao` default 10%) e `valorFinal`.

### Fluxo 6: Reconciliador
`ReconciliarComprasExtraFinanceiro.run` (CommandLineRunner): no boot, busca `CompraExtra PAGA` sem `Pagamento` correspondente e chama `registrarPagamentoExtraEcommerce` — **cross-module em startup**.

## 5. Regras Específicas
1. **Status ignorados** em todos os cálculos: `CANCELADO`, `NO_SHOW`; "pagamento final" considerado para `EM_EDICAO`, `FOTOS_ENVIADAS_PARA_SELECAO`, `FOTOS_ENTREGUES`, `FINALIZADO`.
2. **Comissão de extras criada direto**: `Indicacao` com `status` String `"PENDENTE"` — não usa o enum/fluxo do módulo `comissao` (que escuta eventos do agenda).
3. **`isClienteBloqueado`** (`:593-599`): faz `agendamentoRepository.findAll()` e filtra em memória.
4. **`labelServico`** duplicado em `FinanceiroService:459-467` e `FinanceiroDashboardService:566-574`.
5. **`@Transactional(readOnly=true)`** de classe no Dashboard/Relatório, mas serviços `FinanceiroService`/`ReceitaService` com gravação.

## 6. Testes
Nenhum teste específico para este módulo. Apenas `CrmApplicationTests` (smoke de contexto).

## 7. Dívidas Técnicas e Melhorias Recomendadas

### 7.1 God classes financeiras (600+608 linhas) — **[CRÍTICO] P1**
- `FinanceiroService` (600) e `FinanceiroDashboardService` (608) concentram agregações, mutações e regras de ~8 módulos.
- **Solução**: dividir por caso de uso — `PagamentoService`, `ExtraVendasService`, `FluxoCaixaQuery`, `DashboardAggregator`, `RepasseCustoQuery`; tornar serviços de leitura `@Transactional(readOnly=true)`.

### 7.2 `findAll()` + agregação em memória — **[CRÍTICO] P1**
- `FinanceiroDashboardService.java:73-79`: `despesaRepository.findAll()`, `agendamentoRepository.findAll().filter`, `indicacaoRepository.findAll()`; `FinanceiroRelatorioService.java:116, 192, 195, 251, 258` idem; `FinanceiroService.isClienteBloqueado:594`, `calcularFluxoCaixa:307-308` idem.
- **Solução**: queries de agregação SQL (`SUM`/`GROUP BY`/`COUNT`) nos repositórios donos + `@EntityGraph`/JOIN FETCH; paginar lançamentos.

### 7.3 Escrita cross-module (agenda, comissao) — **[CRÍTICO] P1**
- `registrarPagamento` muta status do agendamento (`FinanceiroService.java:477-479`); `adicionarFotoExtra/Video` muta valores (`:517-519, 539-541`); `registrarPagamentoExtraEcommerce` (`:489-493`); `criarComissaoSeNecessario` cria `Indicacao` (`:573-585`).
- **Solução**: eventos de domínio — `PagamentoRegistradoEvent` consumido pela agenda (dona da máquina de estados), `CompraExtraConfirmadaEvent` já existe; comissão de extras via evento `ComissaoSolicitadaEvent` (módulo comissao é dono) — nunca criar `Indicacao` aqui.

### 7.4 Exposição de entidades na API — **P1**
- `FinanceiroController` retorna `Pagamento`, `FotoExtra`, `VideoExtra`, `List<Pagamento>` como entities (`FinanceiroController.java:46, 55, 69, 83`).
- **Solução**: DTOs (`PagamentoResponse`, `FotoExtraResponse`, `VideoExtraResponse`) com MapStruct.

### 7.5 Duplicação de regra financeira (partilha/repasse/lucro) — **P1**
- Regras de lucro/repasse/margem repetidas entre `FinanceiroService.resumoPorAgendamento` (`:210-299`), `FinanceiroDashboardService` e módulo `agenda`/`fotografo`.
- **Solução**: componente único `FinanceCalculator`/`PartilhaCalculator` no domínio (chamado pelos módulos), evitando divergência de números entre telas.

### 7.6 Reconciliador em startup (cross-module) — **P2**
- `ReconciliarComprasExtraFinanceiro` (CommandLineRunner) usa `CompraExtraRepository` do ecommerce e roda a cada boot.
- **Solução**: mover para job/lock e publicar evento; separar responsabilidade.

### 7.7 Exceções genéricas `IllegalArgumentException`/`orElseThrow()` — **P2**
- `orElseThrow()` sem mensagem (`:95, 470, 507, 529`) e `IllegalArgumentException` em ReceitaService.
- **Solução**: hierarquia central `BusinessException`/`NotFoundException`.

### 7.8 Painéis frontend dependentes de `AgendamentoResponse` — **P2**
- `calcularRelatorios` (`:206`) mapeia `AgendamentoResponse.of` (DTO do módulo agenda) como contrato de relatório.
- **Solução**: DTO próprio do financeiro (`RelatorioAgendamentoItem`) para não vazar contrato do agenda.

### 7.9 Duplicidades internas — **P3**
- `calcularResumo` vs `calcularRelatorios` (`:110-168` vs `:171-208`) quase idênticos; `labelServico` duplicado; `emPeriodo` definido 4 vezes (FinanceiroService, Dashboard, Relatorio).
- **Solução**: extrair `DateRangeValueObject` + `RelatorioTotaisCalculator`.

### 7.10 Herança `BaseEntity` → composição — **P1** (padrão-aplicável)
- Todas as entidades estendem `@MappedSuperclass`.
- **Solução**: `@Embeddable AuditInfo` + Auditing; eliminar `BaseEntity`/`@SuperBuilder`.

### 7.11 DTOs manuais — **P2**
- ~16 records com `static of(...)` escritos à mão.
- **Solução**: MapStruct (decisão aprovada).

## 8. Exemplos de arquivos afetados
- `FinanceiroService.java:94-107, 110-208, 469-547` — agregações e mutações cross-module; `:469-483` muda status do agendamento; `:549-586` cria `Indicacao` direto; `:593-599` — `findAll()`.
- `FinanceiroDashboardService.java:69-98, 73-79, 219-232` — tudo em memória; `:566-574` — label duplicado.
- `FinanceiroRelatorioService.java:116, 192-195, 250-261` — `findAll()` e filtro em memória.
- `FinanceiroController.java:44-85` — expõe entidades.
- `ReconciliarComprasExtraFinanceiro.java:32-46` — runner cross-module.
- `FinanceiroEventListener.java:28-33` — consome evento corretamente (modelo a seguir).