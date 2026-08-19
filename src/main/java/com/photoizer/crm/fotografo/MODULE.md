# Módulo: Fotografo

## 1. Responsabilidade
Gestão de fotógrafos (CRUD sobre `auth.User` com papel `FOTOGRAFO`), relatórios de desempenho financeiro por fotógrafo (dashboard individual, resumo financeiro, relatório global, exportação CSV) e pagamento em lote de repasses. Não possui entidade própria no módulo — opera sobre dados da `auth`, `agenda` e `despesa`. Diferente dos demais módulos, comunica-se por **importação direta de repositórios de outros módulos** (sem Application Events entre fotografo→outros).

## 2. Estrutura
```
fotografo/
├── api/
│   ├── FotografoController.java            # CRUD de fotógrafos + toggle status + CSV
│   ├── RepasseController.java              # dashboard, resumo, relatório global, pagamento em lote
│   ├── ParceiroController.java             # listagem de User papel FOTOGRAFO
│   ├── CriarFotografoRequest.java          # Record @Valid: nome, email, senha, telefone
│   ├── AtualizarFotografoRequest.java      # Record @Valid: nome, email, telefone
│   ├── FotografoDashboardResponse.java     # Record individual (totais + últimos ensaios)
│   ├── FotografoEnsaiosResponse.java       # Record de ensaio no dashboard
│   ├── FotografoRelatorioGlobalResponse.java # Record agregado + itens por fotógrafo
│   └── FotografoResumoFinanceiroResponse.java # Record com custos por categoria/ensaio
├── repository/                             # (vazio — usa repos de outros módulos)
└── service/
    ├── FotografoService.java               # CRUD + agregados + relatórios (~289 linhas)
    └── FotografoCsvExporter.java           # Exportação CSV com BOM UTF-8
```

## 3. Dependências Externas

### Módulos internos importados — **[VIOLAÇÕES Modulith]**
O módulo acessa dados de 3 módulos de negócio por importação direta de repo/service:
| Módulo | Uso |
|--------|-----|
| **agenda** | `AgendamentoFotografoRepository` (ensaios/repasses), `AgendamentoFotografoService` (pagar repasse em lote — usado direto pelo `RepasseController`) |
| **despesa** | `DespesaRepository` (`sumPorFotografo`, `sumPorCategoria`) |
| **auth** | `UserRepository`, `Papel` (CRUD e listagem de fotógrafos) |
| **shared** | `FileStorageService` (persistir CSV gerado) |

### Módulos que dependem deste
Nenhum (módulo folha).

### Eventos
Não publica nem consome eventos.

## 4. Fluxos Principais

### Fluxo 1: CRUD de Fotógrafo
1. `POST /api/v1/fotografos` → `FotografoService.criar()`: valida e-mail único (`existsByEmail`), cria `User` com `PasswordEncoder` + papel `FOTOGRAFO` — **duplica `auth.UserService`**.
2. `PUT /{id}` → `atualizar()`: nome/email/telefone (sem senha).
3. `PATCH /{id}/status` → `toggleStatus()`: alterna `ativo`.
4. `GET /{id}` → `buscarPorId()`: **`RuntimeException` se não achar** e busca por `listarFotografos().stream().filter()` (lista toda → filtra em memória).

### Fluxo 2: Relatórios por Fotógrafo
1. `GET /{id}/dashboard` → `dashboard()`: agrega ensaios do fotógrafo + últimos ensaios (N+1 por ensaio).
2. `GET /{id}/resumo-financeiro` → `resumoFinanceiro()`: agrega por status (comparação de enum por **string**), custos por categoria e por ensaio.
3. `GET /{id}/relatorio-global` → `relatorioGlobal()`: itera todos os fotógrafos chamando `dashboard()` individual → **N consultas agregadas**.
4. `GET /relatorio.csv` → `FotografoCsvExporter`: gera CSV, salva via `FileStorageService`.

### Fluxo 3: Pagamento de Repasses
1. `POST /repasses/pagar-lote` (body `List<UUID>`) → `RepasseController` chama **`AgendamentoFotografoService` do módulo agenda** diretamente (atravessa encapsulamento).
2. **Regra de partilha**: repasse = `valorTotal * partilhaPct` − custos do fotógrafo (despesas); `totalRepassesPendentes/Realizados` distinguem `AGUARDANDO_REPASSE` vs `REPASSE_PAGO`.

## 5. Regras Específicas
1. **Entidade `User` exposta na API**: `listar()` (`FotografoController`), `buscarPorId` e `ParceiroController.listar()` retornam `List<User>`/`User` — a serialização JPA inclui o campo `password` (hash BCrypt) no JSON.
2. **Busca O(N)**: `buscarPorId` usa `listarFotografos().stream().filter()` em vez de `findById` (`FotografoService.java:45-56`).
3. **Enum por string**: `a.getStatus().name().equals("CONFIRMADO")` (`FotografoService.java:236`) em vez de `== StatusAgendamento.CONFIRMADO`.
4. **CRUD duplicado**: `FotografoService.criar/atualizar/toggleStatus` reimplementa regras do `auth.UserService`; `ParceiroController` duplica a listagem por papel.

## 6. Testes
Nenhum teste específico para este módulo. Apenas `CrmApplicationTests` (smoke de contexto).

## 7. Dívidas Técnicas e Melhorias Recomendadas

### 7.1 Exposição de `User` (com `password`) na API — **[CRÍTICO] P1**
- `FotografoController.listar()` retorna `List<User>`; `ParceiroController:27-29` retorna `List<User>`; `buscarPorId` retorna `User`. O JSON serializa até o `password` (hash BCrypt).
- **Solução**: criar `FotografoResponse` (record/DTO) com MapStruct (migração total já aprovada); jamais serializar campo de senha; retornar só `id`, `nome`, `email`, `telefone`, `ativo`, `papel`.

### 7.2 Busca O(N) e N+1 nos relatórios — **P1**
- `buscarPorId` filtra em memória (`FotografoService.java:45-56`); `relatorioGlobal` chama `dashboard` por fotógrafo e o dashboard carrega ensaios/fotos por item (`FotografoService.java:118-145, 193-225`).
- **Solução**: `findById` direto no repo de outro módulo (via facade do módulo dono) e agregações SQL (`SUM`, `GROUP BY`) no repositório da agenda.

### 7.3 Duplicação do CRUD de usuários — **P1**
- `FotografoService.criar/atualizar/toggleStatus` reimplementa `auth.UserService`; duas fontes de verdade para mutação de `User`.
- **Solução**: delegar ao `UserService`/facade do módulo `auth` (ou evento `FotografoCadastradoEvent`); manter neste módulo apenas regras de relatório.

### 7.4 Repasses atravessam módulos — **P1**
- `RepasseController:33-36` injeta `AgendamentoFotografoService` (agenda) e chama operação de domínio da agenda; `DespesaRepository` (despesa) também é injetado.
- **Solução**: tornar o módulo `agenda` dono do repasse (operação + evento `RepasseQuitadoEvent`); consumir estado via eventos/consultas públicas.

### 7.5 Enum comparado por string — **P2**
- `FotografoService.java:236`: `a.getStatus().name().equals("CONFIRMADO")`.
- **Solução**: `getStatus() == StatusAgendamento.CONFIRMADO` (type-safe, compilável).

### 7.6 Exceção genérica `RuntimeException` — **P2**
- `FotografoService.java:53` lança `RuntimeException` ao não achar fotógrafo → 500 em vez de 404.
- **Solução**: usar a hierarquia central `BusinessException`/`NotFoundException` (decisão aprovada).

### 7.7 Regra financeira duplicada (partilha) — **P2**
- Cálculo de partilha/repasse/lucro repetido entre `FotografoService`, `AgendamentoService` e `AgendamentoFotografoService`.
- **Solução**: extrair componente `PartilhaCalculator` único no domínio da agenda e referenciá-lo.

### 7.8 Relatórios sem paginação e agregados em memória — **P2**
- `resumoFinanceiro`/`dashboard` montam listas em memória (`FotografoService.java:227-289`).
- **Solução**: agregados no banco (Projections, `@Query` com `SUM`/`GROUP BY`) no módulo dono.

### 7.9 DTOs `of(...)` manuais e mistura de responsabilidades — **P3**
- Converters estáticos `of()` nos records; `FotografoRelatorioGlobalResponse`/`Resumo` com records aninhados mistos.
- **Solução**: MapStruct (decisão aprovada) + records dedicados por agregação.

## 8. Exemplos de arquivos afetados
- `FotografoController.java:40-69` — retorna `List<User>`; `ParceiroController.java:27-29` — retorna `List<User>` (senha exposta).
- `FotografoService.java:21-56` — CRUD duplicado + `buscarPorId` O(N) + `RuntimeException`; `:118-145` — relatório global N; `:193-225` — N+1 dashboard; `:236` — enum por string.
- `RepasseController.java:33-36,55-62` — uso direto de `AgendamentoFotografoService` (violação Modulith).