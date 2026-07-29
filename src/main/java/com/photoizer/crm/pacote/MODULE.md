# Módulo: Pacote

## 1. Responsabilidade
Gerencia os pacotes de ensaio fotográfico. Define serviços (quantidade de fotos, vídeos), valores (valorBase, precoFotoExtra), regras (bloqueiaDiaInteiro), fotógrafo responsável e editor responsável. É um módulo referenciado por `agenda`, `ecommerce` e `financeiro`.

## 2. Estrutura
```
pacote/
├── model/
│   └── Pacote.java               # Entidade JPA (extends BaseEntity): nome, quantidadeFotos, valorBase, precoFotoExtra, bloqueiaDiaInteiro, ativo, fotografo, editorResponsavel
├── repository/
│   └── PacoteRepository.java     # JpaRepository + findByNomeContainingIgnoreCase
├── service/
│   └── PacoteService.java        # 122 linhas: CRUD + validarAtivo + busca entity vs response
├── api/
│   ├── PacoteController.java     # CRUD REST (GET, POST, PUT, DELETE)
│   ├── PacoteRequest.java        # Record: nome, descricao, quantidadeFotos, valorBase, ..., fotografoId, editorResponsavelId
│   └── PacoteResponse.java       # Record: id, nome, ..., fotografo, editorResponsavel (como UserResponse)
└── exception/
    ├── PacoteNaoEncontradoException.java  # RuntimeException com UUID
    └── PacoteInativoException.java        # RuntimeException com UUID
```

## 3. Dependências Externas

### Módulos internos
| Módulo | Uso |
|--------|-----|
| **auth** | `User`, `UserRepository` (fotografo + editorResponsavel) |
| **shared** | `BaseEntity`, `PageResponse` |

### Módulos que dependem deste
| Módulo | Uso |
|--------|-----|
| **agenda** | `PacoteRepository`, `Pacote`, `PacoteNaoEncontradoException`, `PacoteInativoException` |
| **ecommerce** | `PacoteRepository` (PedidoService) |
| **financeiro** | `PacoteRepository`, `Pacote` |

### Eventos
Nenhum.

## 4. Fluxos Principais

### Fluxo 1: CRUD de Pacotes
- `POST /api/v1/pacotes` → cria (recebe `PacoteRequest` com fotografoId + editorResponsavelId)
- `GET /api/v1/pacotes?search=&page=&perPage=` → lista paginado com busca por nome
- `GET /api/v1/pacotes/all` → lista todos sem paginação
- `GET /api/v1/pacotes/{id}` → busca por ID
- `PUT /api/v1/pacotes/{id}` → atualiza (merge manual campo a campo)
- `DELETE /api/v1/pacotes/{id}` → deleta

### Fluxo 2: Validação de Pacote Ativo
- `PacoteService.validarAtivo(id)` → usado pelo módulo `agenda` ao criar/atualizar agendamentos
- Se `pacote.ativo == false` → lança `PacoteInativoException`

## 5. Regras Específicas
1. **Dois métodos de busca**: `listarTodos()` (sem paginação) e `listarPaginado()` (com paginação e search).
2. **`buscarEntityPorId()` vs `buscarPorId()`**: Um retorna entidade JPA, outro retorna `PacoteResponse` — útil para módulos consumidores que precisam da entidade.
3. **`PacoteResponse` inclui `UserResponse`**: O response serializa o fotógrafo e editor como objetos aninhados (não apenas IDs).
4. **`precoFotoExtra` default R$ 15,00**: Se não informado no request, usa `BigDecimal.valueOf(15)`.
5. **Controller retorna `ResponseEntity<?>` em `listar()`**: Porque retorna `PageResponse<PacoteResponse>` em vez de `List`.

## 6. Testes
Nenhum teste específico para este módulo.

## 7. Pontos de Atenção
- **`PacoteService.atualizar` faz merge manual**: Todos os campos são copiados um a um. Qualquer novo campo exige alteração no método.
- **`fotografo` e `editorResponsavel` são `@ManyToOne(fetch = LAZY)`**: PacoteResponse serializa esses objetos, o que pode causar `LazyInitializationException` se a transação já fechou.
- **Endpoint `/all` sem paginação**: Pode retornar muitos dados se houver muitos pacotes.
- **`precoFotoExtra` duplicado**: Existe em `Pacote` (valor base) e em `ConfiguracaoService` (valor global). Qual prevalece depende do módulo consumidor.
