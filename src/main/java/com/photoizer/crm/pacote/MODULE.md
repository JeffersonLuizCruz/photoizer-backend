# Módulo: Pacote

## 1. Responsabilidade
Gerencia os pacotes de ensaio fotográfico: quantidade de fotos/vídeos, valores (`valorBase`, `precoFotoExtra`), regras (`bloqueiaDiaInteiro`), `imagemCapa`, `beneficios`, `diasParaEntrega` e status `ativo`. Referenciado por `agenda` (cálculo de valores), `ecommerce` e `financeiro`.

## 2. Estrutura
```
pacote/
├── model/
│   └── Pacote.java               # Entidade JPA: nome, descricao, quantidadeFotos/Videos, valorBase, precoFotoExtra, imagemCapa, beneficios, duracaoEstimada, bloqueiaDiaInteiro, ativo, diasParaEntrega
├── repository/
│   └── PacoteRepository.java     # JpaRepository + findByNomeContainingIgnoreCase (Page)
├── service/
│   ├── PacoteService.java        # Command service: criar, atualizar, deletar
│   └── PacoteQueryService.java   # Query service (fachada read-only): buscarPorId, listarTodos, listarPaginado, validarAtivo
├── api/
│   ├── PacoteController.java     # CRUD REST + GET /all (sem paginação)
│   ├── PacoteMapper.java         # MapStruct mapper: toResponse, toEntity, updateEntity
│   ├── PacoteRequest.java        # Record com @Valid (inclui precoFotoExtra opcional)
│   └── PacoteResponse.java       # Record (sem static of — usa MapStruct)
└── exception/
    ├── PacoteNaoEncontradoException.java  # RuntimeException com UUID
    └── PacoteInativoException.java        # RuntimeException com UUID
```

## 3. Dependências Externas

### Módulos internos
- **shared** → `AuditInfo`, `PageResponse`
- **MapStruct** → `PacoteMapper` (mapper pattern)

### Módulos que dependem deste
| Módulo | Uso | Via |
|--------|-----|-----|
| agenda | Cálculo de valores, validação ativo | `PacoteQueryService` (fachada) |
| ecommerce | Galeria/checkout | `Agendamento.getPacote()` (lazy loading) |
| financeiro | Preview, cálculos | `PacoteQueryService` (fachada) |
| contrato | Dados no contrato | `PacoteQueryService` (fachada) |

### Eventos
Nenhum.

## 4. Fluxos Principais

### Fluxo 1: CRUD de Pacotes
- `POST /api/v1/pacotes` → cria; `precoFotoExtra` default R$ 15,00 se ausente (via mapper).
- `GET /api/v1/pacotes?search=&page=&perPage=` → paginado por nome.
- `GET /api/v1/pacotes/all` → todos sem paginação.
- `GET /api/v1/pacotes/{id}`, `PUT`, `DELETE`.
- `atualizar` usa **MapStruct `updateEntity`** (`PacoteMapper.updateEntity`).

### Fluxo 2: Validação de Disponibilidade
- `validarAtivo(id)` (`PacoteQueryService`) → usado por `agenda`: lança `PacoteInativoException` se `ativo == false`.

## 5. Regras Específicas
1. **`precoFotoExtra` default `BigDecimal.valueOf(15)`** tratado de forma uniforme entre criar/update — no mapper `toEntity` e `updateEntity` via expressão Java.
2. **`PacoteResponse`** não mais inclui `valorTotalMinimo` (campo redundante removido — era igual a `valorBase`).
3. **`listarTodos` (sem paginação) + `listarPaginado`**: dois caminhos de consulta no `PacoteQueryService`, sem `Specification` unificada (P3 pendente).
4. **Sem fotografo/editorResponsavel na entidade** (relacionamento removido; `Pacote` não referencia `User`).
5. **Controller `listar()` retorna `ResponseEntity<PageResponse<PacoteResponse>>`** — tipagem estática corrigida.

## 6. Testes
Nenhum teste específico.

## 7. Dívidas Técnicas e Melhorias Recomendadas

### 7.1 Merge manual no `atualizar` — **RESOLVIDO**
- ✅ MapStruct `PacoteMapper.updateEntity()` substituiu merge de 13 campos.

### 7.2 `PacoteResponse.valorTotalMinimo` mal definido — **RESOLVIDO**
- ✅ Campo `valorTotalMinimo` removido do `PacoteResponse`.

### 7.3 Inconsistência de defaults criar/atualizar — **RESOLVIDO**
- ✅ Default `precoFotoExtra = 15` tratado de forma uniforme via mapper.

### 7.4 `ResponseEntity<?>` no controller — **RESOLVIDO**
- ✅ `listar()` retorna `ResponseEntity<PageResponse<PacoteResponse>>`.

### 7.5 Injeção indevida de `PacoteRepository` — **RESOLVIDO (P1)**
- ✅ Criado `PacoteQueryService` como fachada read-only.
- ✅ `agenda`, `contrato` e `financeiro` agora usam `PacoteQueryService` em vez de `PacoteRepository`.

### 7.6 Separação Command/Query — **RESOLVIDO (P1)**
- ✅ `PacoteService` (command) → criar, atualizar, deletar.
- ✅ `PacoteQueryService` (query) → buscarPorId, listarTodos, listarPaginado, validarAtivo.

### 7.7 `findByNomeContainingIgnoreCase` sem `Specification` — **P3 pendente**
- Busca por nome é candidata a `Specification` para combinar com filtros futuros (ex.: `ativo`, faixa de preço) sem multiplicar métodos.

### 7.8 `boolean` primitivos `ativo`/`bloqueiaDiaInteiro` — **P3 pendente**
- `PacoteRequest` valida `quantidadeFotos > 0`, `valorBase > 0` etc. Mas `ativo` e `bloqueiaDiaInteiro` são `boolean` primitivos — sem distinção null/true. Considerar `Boolean` + validação se nulo for indesejado.

## 8. Arquivos afetados (refactor)
| Arquivo | Alteração |
|---------|-----------|
| `PacoteMapper.java` | **Novo** — MapStruct mapper (toResponse, toEntity, updateEntity) |
| `PacoteQueryService.java` | **Novo** — fachada read-only para módulos consumidores |
| `PacoteService.java` | Refatorado — usa `PacoteMapper`; separado em command service |
| `PacoteController.java` | Refatorado — usa `PacoteQueryService` para leituras; `ResponseEntity<?>` → `ResponseEntity<PageResponse<PacoteResponse>>` |
| `PacoteResponse.java` | Refatorado — removido `static of()` e campo `valorTotalMinimo` |
| `AgendamentoService.java` | `PacoteRepository` → `PacoteQueryService` |
| `GestaoContratoService.java` | `PacoteRepository` → `PacoteQueryService` |
| `FinanceiroQueryService.java` | `PacoteRepository` → `PacoteQueryService` |
