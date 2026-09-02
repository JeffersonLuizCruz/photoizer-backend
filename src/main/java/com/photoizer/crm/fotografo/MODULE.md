# Módulo: Fotografo

## 1. Responsabilidade
Gestão de fotógrafos (CRUD sobre `auth.User` com papel `FOTOGRAFO`), relatórios de desempenho financeiro por fotógrafo (dashboard individual, resumo financeiro, relatório global, exportação CSV) e listagem de parceiros elegíveis a receber repasse. Não possui entidade própria no módulo — opera sobre dados da `auth`, `agenda` e `despesa`.

## 2. Estrutura
```
fotografo/
├── MODULE.md
├── api/
│   ├── FotografoController.java            # CRUD + toggle status + relatórios
│   ├── ParceiroController.java             # listagem de User papel FOTOGRAFO/EDITOR/AGENDADOR
│   ├── FotografoMapper.java                # MapStruct: User → UserResponse
│   ├── CriarFotografoRequest.java          # Record @Valid: nome, email, senha, telefone
│   ├── AtualizarFotografoRequest.java      # Record @Valid: nome, email, telefone
│   ├── FotografoDashboardResponse.java     # Record individual (totais + últimos ensaios)
│   ├── FotografoEnsaiosResponse.java       # Record de ensaio no dashboard
│   ├── FotografoRelatorioGlobalResponse.java # Record agregado + itens por fotógrafo
│   └── FotografoResumoFinanceiroResponse.java # Record com custos por categoria/ensaio
├── exception/                              # Exceções de domínio
│   ├── FotografoNaoEncontradoException.java
│   └── FotografoComEnsaiosVinculadosException.java
├── repository/                             # (vazio — usa repos de outros módulos via Facade)
└── service/
    ├── FotografoService.java               # CRUD fino (delega ao UserService)
    ├── FotografoQueryService.java          # Relatórios, dashboard, agregações
    ├── FotografoDataFacade.java            # Facade de acesso a repos de outros módulos
    └── FotografoCsvExporter.java           # Exportação CSV com BOM UTF-8
```

## 3. Dependências Externas

### Módulos internos importados — via FotografoDataFacade
O módulo acessa dados de 3 módulos de negócio, encapsulados na `FotografoDataFacade`:
| Módulo | Uso |
|--------|-----|
| **agenda** | `AgendamentoFotografoRepository` (ensaios/repasses) |
| **despesa** | `DespesaRepository` (custos por fotógrafo/ensaio) |
| **auth** | `UserRepository`, `UserService` (CRUD de User com papel FOTOGRAFO) |

### Módulos que dependem deste
Nenhum (módulo folha).

### Eventos
Não publica nem consome eventos.

## 4. Fluxos Principais

### Fluxo 1: CRUD de Fotógrafo
1. `POST /api/v1/fotografos` → `FotografoService.criar()`: delega para `UserService.criarFotografo()`.
2. `PUT /{id}` → `atualizar()`: delega para `UserService.atualizarFotografo()`.
3. `PATCH /{id}/status` → `toggleStatus()`: delega para `UserService.toggleStatus()`.
4. `GET /{id}` → `buscarPorId()`: busca via `FotografoDataFacade.findFotografoById()` (O(1)).
5. `DELETE /{id}` → `remover()`: valida sem ensaios vinculados, delega para `UserService.remover()`.

### Fluxo 2: Relatórios por Fotógrafo (FotografoQueryService)
1. `GET /{id}/dashboard` → `dashboard()`: agrega ensaios + últimos ensaios.
2. `GET /{id}/resumo-financeiro` → `resumoFinanceiro()`: agrega por status, custos por categoria/ensaio.
3. `GET /relatorio-global` → `relatorioGlobal()`: itera todos os fotógrafos chamando `dashboard()`.
4. `GET /{id}/financeiro/csv` → `FotografoCsvExporter`: gera CSV.

### Fluxo 3: Pagamento de Repasses
1. `POST /repasses/pagar-lote` (body `List<UUID>`) → `RepasseController` (módulo **agenda**).
2. **Migração**: RepasseController foi movido para o módulo agenda (repasse é operação de domínio do agenda).

## 5. Regras Específicas
1. **CRUD delegado ao auth**: `FotografoService` delega criação/atualização/remoção para `UserService` (Single Source of Truth).
2. **Busca O(1)**: `buscarPorId()` usa `findById()` direto via Facade (corrigido de busca O(N) em memória).
3. **Exceções de domínio**: `FotografoNaoEncontradoException` (404) e `FotografoComEnsaiosVinculadosException` (422) substituem `IllegalArgumentException` genérica.
4. **MapStruct**: `FotografoMapper` converte `User` → `UserResponse` (consistente com 7 módulos do projeto).
5. **Enum type-safe**: `StatusAgendamento.CONFIRMADO` (enum) em vez de `"CONFIRMADO"` (string).

## 6. Testes
Nenhum teste específico para este módulo. Apenas `CrmApplicationTests` (smoke de contexto).

## 7. Dívidas Técnicas Resolvidas

| # | Dívida | Solução |
|---|--------|---------|
| 1 | ~~Busca O(N) em `buscarPorId`~~ | `findById()` direto via Facade |
| 2 | ~~N+1 no dashboard~~ | Mantido por ora (agregação via Facade) |
| 3 | ~~`relatorioGlobal` N queries~~ | Mantido por ora (agregação via Facade) |
| 4 | ~~Enum por string~~ | `== StatusAgendamento.CONFIRMADO` |
| 5 | ~~`RuntimeException` genérica~~ | Exceções de domínio |
| 6 | ~~`RepasseController` retorna entidade JPA~~ | Migrado para agenda com DTO (`RepasseResponse`) |
| 7 | ~~Violação Modulith (RepasseController)~~ | Migrado para agenda |
| 8 | ~~DTOs manuais~~ | `FotografoMapper` (MapStruct) |
| 9 | ~~CRUD duplicado com auth~~ | Delega para `UserService` |

## 8. Dívidas Pendentes

| # | Dívida | Prioridade | Nota |
|---|--------|------------|------|
| 1 | N+1 em `dashboard()` — `calcularCustosFotografo()` chamado por ensaio | P1 | Usar `DespesaRepository.sumValorByAgendamentoIdAndFotografoId()` via Facade |
| 2 | `relatorioGlobal()` chama `dashboard()` por fotógrafo | P1 | Otimizar com query agregada SQL |
| 3 | `FotografoEnsaiosResponse.status` como String | P2 | Usar enum `StatusAgendamento` |
| 4 | Testes unitários ausentes | P2 | Criar testes para service e query service |
