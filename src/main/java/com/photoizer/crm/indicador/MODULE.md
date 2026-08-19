# Módulo: Indicador

## 1. Responsabilidade
Gerencia o cadastro de indicadores — pessoas que indicam clientes e recebem comissão. Módulo auxiliar usado pelos módulos `comissao` e `financeiro` para buscar/criar indicadores durante o fluxo de agendamento.

## 2. Estrutura
```
indicador/
├── model/
│   └── Indicador.java          # Entidade JPA (extends BaseEntity): nome, telefone, observacoes, percentualComissao
├── repository/
│   └── IndicadorRepository.java # JpaRepository + findByNomeAndTelefone, search (JPQL LIKE), existsByNomeAndTelefone
├── service/
│   └── IndicadorService.java   # CRUD + buscarOuCriar (upsert nome+telefone com retry)
├── api/
│   ├── IndicadorController.java # CRUD + toResponse com totais de comissões (consultando módulo comissao)
│   ├── IndicadorRequest.java    # Record: nome, telefone, observacoes, percentualComissao (@Valid)
│   └── IndicadorResponse.java   # Record + static of(Indicador, totalPendente, totalPago, totalIndicacoes)
└── exception/
    └── (vazio — usa RuntimeException genérica)
```

## 3. Dependências Externas

### Módulos internos importados — **[VIOLAÇÕES Modulith]**
- **comissao** → `IndicacaoRepository`/`Indicacao` injetados em `IndicadorController.java:3-4,24-27` para somar comissões do indicador.

### Módulos que dependem deste
| Módulo | Uso |
|--------|-----|
| comissao | `IndicacaoListener` usa `IndicadorService.buscarOuCriar()` |
| financeiro | `FinanceiroService.criarComissaoSeNecessario()` usa `IndicadorService.buscarOuCriar()` |

### Eventos
Nenhum.

## 4. Fluxos Principais

### Fluxo 1: CRUD de Indicadores
- `GET /api/v1/indicadores?search=` → lista com busca opcional (JPQL `LIKE` em nome/telefone).
- `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}` → CRUD padrão. `POST`/`PUT` finalizam com `toResponse()`.

### Fluxo 2: `criar()` — upsert com merge
- Se já existe `nome+telefone`: atualiza `observacoes`/`percentualComissao` se não nulos e salva (`IndicadorService.java:36-43`).
- Senão: cria novo registrador.

### Fluxo 3: `buscarOuCriar()` — usado por outros módulos
- Busca por `nome+telefone`; se ausente cria (`IndicadorService.java:69-79`). Try/catch com re-busca para concorrência.

### Fluxo 4: Resposta enriquecida com totais
- `IndicadorController.toResponse()` (`:73-84`): `indicacaoRepository.findByIndicadorId(id)` e soma em memória `PENDENTE`/`PAGA`.

## 5. Regras Específicas
1. **Unique constraint `(nome, telefone)`** na tabela (`Indicador.java:20-22`).
2. **`toResponse` consulta o módulo `comissao` diretamente** (`IndicadorController.java:74`) — viola isolamento Modulith e gera **N+1** na listagem.
3. **Sem exceção de domínio**: usa `RuntimeException` (`IndicadorService.java:33,64,76`).
4. **`dataCadastro`/auditoria**: herda de `BaseEntity`.

## 6. Testes
Nenhum teste específico.

## 7. Dívidas Técnicas e Melhorias Recomendadas

### 7.1 Ni+1 na listagem + acoplamento com `comissao` — **P1**
- Para cada indicador, `toResponse` dispara `findByIndicadorId` (`IndicadorController.java:74`) — 1 query por item. E o controller conhece o repositório de outro módulo.
- **Solução**: 
  - Mover a agregação de comissões para o módulo **comissao** (que é dono dos dados) com uma query agregada (`GROUP BY indicadorId SUM`) em Java: `List<IndicadorComissaoAgregado>`; expor via service/evento.
  - Em alternativa, fazer agregação única via `IndicacaoRepository.findTotalPendenteByIndicadorIds(...)` com `GROUP BY`.

### 7.2 Exceções genéricas — **P1**
- `RuntimeException` em `buscarPorId`/`remover` (404 esperados) cai no handler 500 (`IndicadorService.java:33,64`).
- **Solução**: criar `IndicadorNaoEncontradoException` (ou usar `NotFoundException` central do plano de `shared`) − ver `shared/MODULE.md §7.3`.

### 7.3 `criar()` com merge implícito (efeito colateral) — **P2**
- `POST /api/v1/indicadores` atualiza registro existente em vez de informar duplicidade (`IndicadorService.java:37-42`). Comportamento surpreendente para um endpoint de criação.
- **Solução**: lançar conflito em `POST` se já existir, mantendo o merge apenas em `buscarOuCriar()` (uso interno) e `PUT`.

### 7.4 `buscarOuCriar` com try/catch frágil — **P3**
- O retry por exceção (`IndicadorService.java:71-77`) pode mascarar erros reais de persistência. 
- **Solução**: usar `existsByNomeAndTelefone` + unique constraint como proteção no banco (catch de `DataIntegrityViolationException` apenas para o caso único).

### 7.5 `IndicadorResponse.of` manual — **P3**
- Factory manual (`IndicadorResponse.java:18-22`) candidata a **MapStruct**; campos calculados (totais) ficam via `@AfterMapping`/`default`.

### 7.6 Service recebendo parâmetros individuais — **P2**
- `criar(nome, telefone, observacoes, percentualComissao)` e `atualizar(...)` têm 4-5 argumentos (`IndicadorService.java:36,53`). 
- **Solução (Clean Architecture)**: receber um command/record (`IndicadorCommand` ou o próprio `IndicadorRequest` mapeado) para evitar confusão de posição de args.

## 8. Exemplos de arquivos afetados
- `IndicadorController.java:74-84` — N+1 + consulta cross-module; `IndicadorService.java:33,64,76` — `RuntimeException` → exceção de domínio; `IndicadorService.java:36-43` — merge no POST; `IndicadorResponse.java:18-22` — factory manual; `Indicador.java:20-22` — unique constraint (manter).