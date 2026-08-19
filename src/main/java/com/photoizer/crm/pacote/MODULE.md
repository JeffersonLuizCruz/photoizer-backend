# Módulo: Pacote

## 1. Responsabilidade
Gerencia os pacotes de ensaio fotográfico: quantidade de fotos/vídeos, valores (`valorBase`, `precoFotoExtra`), regras (`bloqueiaDiaInteiro`), `imagemCapa`, `beneficios`, `diasParaEntrega` e status `ativo`. Referenciado por `agenda` (cálculo de valores), `ecommerce` e `financeiro`.

## 2. Estrutura
```
pacote/
├── model/
│   └── Pacote.java               # Entidade JPA (extends BaseEntity): nome, descricao, quantidadeFotos/Videos, valorBase, precoFotoExtra, imagemCapa, beneficios, duracaoEstimada, bloqueiaDiaInteiro, ativo, diasParaEntrega
├── repository/
│   └── PacoteRepository.java     # JpaRepository + findByNomeContainingIgnoreCase (Page)
├── service/
│   └── PacoteService.java        # CRUD + listarTodos/listarPaginado + validarAtivo
├── api/
│   ├── PacoteController.java     # CRUD REST + GET /all (sem paginação)
│   ├── PacoteRequest.java        # Record com @Valid (inclui precoFotoExtra opcional)
│   └── PacoteResponse.java       # Record + static of(Pacote) (inclui valorTotalMinimo)
└── exception/
    ├── PacoteNaoEncontradoException.java  # RuntimeException com UUID
    └── PacoteInativoException.java        # RuntimeException com UUID
```

## 3. Dependências Externas

### Módulos internos
- **shared** → `BaseEntity`, `PageResponse`

### Módulos que dependem deste
| Módulo | Uso |
|--------|-----|
| agenda | `PacoteRepository`/`Pacote`/`PacoteNaoEncontradoException`/`PacoteInativoException` (cálculo de valores no agendamento) |
| ecommerce | `PacoteRepository` (galeria/checkout) |
| financeiro | `PacoteRepository` (comissões/despesas) |
| contrato | `Pacote` (dados no contrato) |

### Eventos
Nenhum.

## 4. Fluxos Principais

### Fluxo 1: CRUD de Pacotes
- `POST /api/v1/pacotes` → cria; `precoFotoExtra` default R$ 15,00 se ausente (`PacoteService.java:67`).
- `GET /api/v1/pacotes?search=&page=&perPage=` → paginado por nome.
- `GET /api/v1/pacotes/all` → todos sem paginação.
- `GET /api/v1/pacotes/{id}`, `PUT`, `DELETE`.
- `atualizar` faz **merge manual campo a campo** (`PacoteService.java:78-94`).

### Fluxo 2: Validação de Disponibilidade
- `validarAtivo(id)` (`PacoteService.java:103-109`) → usado por `agenda`: lança `PacoteInativoException` se `ativo == false`.

## 5. Regras Específicas
1. **`precoFotoExtra` default `BigDecimal.valueOf(15)`** apenas na criação (`criar`), mas **no update preserva o valor atual** se null — tratamentos inconsistentes entre criar/atualizar.
2. **`PacoteResponse.valorTotalMinimo` = `valorBase`** (`PacoteResponse.java:38`) — campo redundante que semânticamente deveria incluir extras, não apenas o valor base.
3. **`listarTodos` (sem paginação) + `listarPaginado`**: dois caminhos de consulta, sem `Specification` unificada.
4. **Sem fotografo/editorResponsavel na entidade** (MODULE.md anterior estava defasado — o relacionamento foi removido; hoje `Pacote` não referencia `User`).
5. **Controller `listar()` retorna `ResponseEntity<?>`** (`PacoteController.java:44`) por usar `PageResponse` — perde tipagem estática.

## 6. Testes
Nenhum teste específico.

## 7. Dívidas Técnicas e Melhorias Recomendadas

### 7.1 Merge manual no `atualizar` — **P1**
- ~13 campos copiados um a um (`PacoteService.java:81-92`). Qualquer campo novo exige alterar este método.
- **Solução**: usar **MapStruct** (`PacoteMapper.atualizar(entidade, request)` com `@MappingTarget`) + `BeanMapping`/`NullValuePropertyMappingStrategy` para os campos opcionais — a regra do `precoFotoExtra` fica via `@Mapping`/`default`.

### 7.2 `PacoteResponse.valorTotalMinimo` mal definido — **P2**
- Duplica `valorBase` (`PacoteResponse.java:38`) sem semântica clara de entrega. Nome e cálculo enganosos.
- **Solução**: definir regra de negócio explícita (base + preço mínimo de extras) e documentar, ou remover o campo do response.

### 7.3 Inconsistência de defaults criar/atualizar — **P2**
- Criação aplica default 15 se null; atualização preserva (`PacoteService.java:67,86`). Comportamento divergente surpreende a API.
- **Solução**: default tratado como regra de domínio única (no mapper ou em método `Pacote.aplicarDefaultPrecoFotoExtra()`).

### 7.4 `ResponseEntity<?>` no controller — **P3**
- `listar()` retorna `ResponseEntity<?>` (`PacoteController.java:44`). Tipar como `ResponseEntity<PageResponse<PacoteResponse>>`.

### 7.5 Hermes de validação ✓ — **P3**
- `PacoteRequest` já valida `quantidadeFotos > 0`, `valorBase > 0` etc. Mas `ativo` e `bloqueiaDiaInteiro` são `boolean` primitivos — sem distinção null/true. Considerar `Boolean` + validação se nulo for indesejado.

### 7.6 `findByNomeContainingIgnoreCase` sem `Specification` — **P3**
- Busca por nome é candidata a `Specification` para combinar com filtros futuros (ex.: `ativo`, faixa de preço) sem multiplicar métodos.

## 8. Exemplos de arquivos afetados
- `PacoteService.java:78-94` — merge manual a migrar para MapStruct; `PacoteResponse.java:38` — campo `valorTotalMinimo` redundante; `PacoteService.java:67,86` — defaults divergentes; `PacoteController.java:44` — retorno sem tipo.