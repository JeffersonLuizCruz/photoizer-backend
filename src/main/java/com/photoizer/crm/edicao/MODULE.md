# Módulo: Edição

## 1. Responsabilidade
Gerencia o processo de edição de fotos do ensaio desde o upload das fotos RAW (fotógrafo) até a edição (editor), revisão individual, conclusão e publicação no e-commerce/loja. É ativado automaticamente quando o pagamento final do agendamento é registrado (via evento). Inclui também download em ZIP de RAW/editadas.

## 2. Estrutura (pós-refactor)
```
edicao/
├── model/
│   ├── Edicao.java              # Entidade JPA: agendamentoId, status, fotografo, editor, datas, observacoes
│   ├── FotoEdicao.java          # Entidade JPA: edicaoId, rawPath, editedPath, status, ordem, aprovado, comentario
│   ├── StatusEdicao.java        # Enum + State Pattern (transições validadas)
│   └── StatusFotoEdicao.java    # Enum: RAW, EDITADO
├── repository/
│   ├── EdicaoRepository.java
│   └── FotoEdicaoRepository.java
├── service/
│   ├── EdicaoService.java           # Orquestrador fino (~190 linhas) — delega para services especializados
│   ├── EdicaoQueryService.java      # Facade read-only (obterStatus, listar, buscarFoto)
│   ├── RawUploadService.java        # Upload de fotos RAW
│   ├── EdicaoUploadEditadasService.java  # Upload de fotos editadas
│   ├── PublicacaoService.java       # Publicação unificada (ecommerce/loja) — Strategy Pattern
│   ├── EdicaoRevisaoService.java    # Revisão individual — Command Pattern (publica eventos)
│   ├── FotoEdicaoProcessor.java     # Delega para FotoProcessingHelper (módulo foto)
│   └── EdicaoZipService.java        # Geração de ZIPs
├── api/
│   ├── EdicaoController.java    # 16 endpoints REST
│   ├── EdicaoMapper.java        # MapStruct mapper
│   ├── EdicaoResponse.java      # Record (sem static of())
│   ├── FotoEdicaoResponse.java  # Record (sem static of())
│   ├── RevisaoRequest.java      # Record @Valid
│   ├── ReordenarFotoRequest.java # Record @Valid (substitui Map<String,Object>)
│   └── ObservacoesRequest.java  # Record @Valid (substitui Map<String,String>)
├── event/
│   ├── RawEnviadosEvent.java
│   ├── EdicaoConcluidaEvent.java
│   └── FotosPublicadasEvent.java
├── listener/
│   └── EdicaoListener.java
└── exception/
    ├── EdicaoBusinessException.java        # Base (nova)
    ├── EdicaoNaoEncontradaException.java   # 404
    ├── FotoEdicaoNaoEncontradaException.java # 404
    ├── FotoSemRawException.java            # 422
    └── StatusEdicaoInvalidoException.java  # 422
```

## 3. Dependências Externas

### Módulos internos importados — **[VIOLAÇÕES Modulith]** (mantidas por decisão)
| Módulo | Uso | Tipo |
|--------|-----|------|
| **agenda** | `Agendamento`, `AgendamentoRepository`, `StatusAgendamento` — service muta status do agendamento | entrada **e escrita** |
| **auth** | `User`, `UserRepository` — `getCurrentUser()` no upload RAW | leitura |
| **foto** | `FotoEnsaio`, `FotoEnsaioRepository`, `StatusFoto` — **resolvido**: eventos `FotoEdicaoPublicadaEvent`/`FotoEdicaoRemovidaEvent` substituem escrita direta | ~~escrita~~ **eventos** |
| **shared** | `AuditInfo`, `FileStorageService` | infraestrutura |

> A boa prática está presente: `EdicaoListener` **consome eventos** (`PagamentoFinalRegistradoEvent`). As violações de escrita cross-module permanecem por decisão do usuário (serão resolvidas quando todos os módulos forem refatorados).

### Eventos consumidos
| Evento | Ação |
|--------|------|
| `PagamentoFinalRegistradoEvent` (agenda) | `EdicaoListener.handlePagamentoFinal` — cria `Edicao` `AGUARDANDO_RAW` se inexistente |

### Eventos publicados
| Evento | Consumidores |
|--------|-------------|
| `RawEnviadosEvent` | — (reservado p/ notificações) |
| `EdicaoConcluidaEvent` | — (reservado) |
| `FotosPublicadasEvent` | — (reservado) |

## 4. Design Patterns Aplicados

| Pattern | Onde | Justificativa |
|---------|------|---------------|
| **State Pattern** | `StatusEdicao.podeTransicionarPara()` | Centraliza regras de transição no enum, elimina `if` espalhado no service |
| **Facade/Query Service** | `EdicaoQueryService` | Separa leitura de escrita, facilita cache e testes |
| **Strategy Pattern** | `PublicacaoService.publicar(tipo)` | Unifica dois fluxos de publicação em uma única operação |
| **Command Pattern** | `EdicaoRevisaoService.revisarFoto()` | Encapsula revisão com efeitos colaterais controlados |
| **Template Method** | `FotoEdicaoProcessor.processar()` | Delega para `FotoProcessingHelper` (módulo foto) para processamento de imagem |
| **MapStruct Mapper** | `EdicaoMapper` | Elimina mapeamento manual, segue padrão do `AgendamentoMapper` |

## 5. Fluxos Principais

### Fluxo 1: Criação automática da Edição
1. `PagamentoFinalRegistradoEvent` publicado pelo módulo `agenda`.
2. `EdicaoListener.handlePagamentoFinal()`: cria `Edicao` `AGUARDANDO_RAW` se `!existsByAgendamentoId` — **único ponto de escuta via evento**.

### Fluxo 2: Upload RAW (Fotógrafo)
`POST /api/v1/edicao/{agendamentoId}/raw` → `RawUploadService.uploadRaw()`:
1. Valida agendamento `EM_EDICAO` ou `AGUARDANDO_PAGAMENTO_FINAL`, senão `StatusEdicaoInvalidoException`.
2. Cria `Edicao` lazy se inexistente (`status = AGUARDANDO_RAW`).
3. Salva cada arquivo em `uploads/{agendamentoId}/raw/`, cria `FotoEdicao.RAW` com `ordem = count + i`.
4. Seta `RAW_ENVIADOS` + `dataEnvioRaw`; vincula fotógrafo via `getCurrentUser()` (N+1: 1 query `User` por chamada).
5. **Efeito colateral cross-module**: se agendamento `AGUARDANDO_PAGAMENTO_FINAL`, muta status do `Agendamento` para `EM_EDICAO`.
6. Publica `RawEnviadosEvent`.

### Fluxo 3: Upload Editadas (Editor)
`POST /agendamentoId/editadas` → `EdicaoUploadEditadasService.uploadEditadas()`:
1. Rejeita se `AGUARDANDO_RAW`.
2. **Match por nome de arquivo**: vincula editada à RAW por `rawFileName == originalFilename`; nome sem RAW → `FotoSemRawException`.
3. Seta `EDITADO` + `editedPath`/`editedFileName`; `Edicao` → `EM_EDICAO`.

### Fluxo 4: Revisão (Aprovação Individual)
`PATCH /fotos/{fotoId}/revisao` → `EdicaoRevisaoService.revisarFoto()`:
- `aprovado=true` + `editedPath`: publica `FotoEdicaoPublicadaEvent` (listener no foto cria `FotoEnsaio.INEDITA` com watermark + thumbnail).
- `aprovado=false`: publica `FotoEdicaoRemovidaEvent` (listener no foto remove `FotoEnsaio.INEDITA` existente).

### Fluxo 5: Conclusão e Publicação
1. `PATCH /concluir` → `EdicaoService.concluirEdicao()`: exige ≥1 editada, seta `EDICAO_CONCLUIDA`, publica `EdicaoConcluidaEvent`.
2. `PATCH /publicar` → `PublicacaoService.publicar(ECOMMERCE)`: valida `EDICAO_CONCLUIDA`; para cada editada publica `FotoEdicaoPublicadaEvent` (listener no foto cria `FotoEnsaio.PUBLICADA`), avança agendamento → `SELECAO_DAS_FOTOS` (**escrita em agenda**), publica `FotosPublicadasEvent`.
3. `PATCH /publicar-loja` → `PublicacaoService.publicar(LOJA)`: publica as `INEDITA` → `PUBLICADA`; fluxo unificado via Strategy Pattern.

### Fluxo 6: Zips e reordenação
- `GET /download-raw`/`download-editadas` → `EdicaoZipService.gerarZipRaw`/`gerarZipEditadas`: cria ZIPs em `uploads/temp/`, limpa ZIPs com mais de 1h.
- `PATCH /fotos/reordenar` → `EdicaoService.reordenarFotos()`: recebe `List<ReordenarFotoRequest>` tipado.

## 6. Regras Específicas
1. **Publicação unificada** (`PublicacaoService.publicar(tipo)`) — dois caminhos (ECOMMERCE/LOJA) em uma única operação via Strategy Pattern.
2. **Ligação arquivo↔RAW por nome** — frágil: usuário precisa renomear arquivo editado idêntico ao RAW.
3. **Watermark + thumbnail** via `FotoEdicaoProcessor` (Template Method) — texto `"© Photoizer Studio"`, opacidade 15%, com fallback ao caminho original em caso de erro.
4. **`getCurrentUser()`** lê `SecurityContext` e busca `UserRepository` a cada `uploadRaw`; retorna `null` para anonymous.
5. **State Pattern** no `StatusEdicao`: transições validadas centralizadamente (`podeTransicionarPara()`).

## 6. Testes
Nenhum teste específico para este módulo. Apenas `CrmApplicationTests` (smoke de contexto).

## 7. Dívidas Técnicas e Status (pós-refactor)

### 7.1 Escrita cross-module em `agenda` e `foto` — **[CRÍTICO] P1**
- `EdicaoService.uploadRaw()` (`:192-195`) **muta `Agendamento.status`** — **pendente**.
- ~~`revisarFoto`/`publicar` **criam/excluem `FotoEnsaio`**~~ **RESOLVIDO**: `PublicacaoService` e `EdicaoRevisaoService` publicam eventos `FotoEdicaoPublicadaEvent`/`FotoEdicaoRemovidaEvent`; listener `FotoEdicaoEventListener` no módulo foto cria/remove `FotoEnsaio`.

### 7.2 `EdicaoService` oversized — **RESOLVIDO** ✅
- Extraídos: `EdicaoQueryService`, `RawUploadService`, `EdicaoUploadEditadasService`, `PublicacaoService`, `EdicaoRevisaoService`, `FotoEdicaoProcessor`, `EdicaoZipService`.
- `EdicaoService` agora é orquestrador fino (~190 linhas).

### 7.3 Herança `BaseEntity` → composição — **RESOLVIDO** ✅
- `Edicao`/`FotoEdicao` usam `@Embeddable AuditInfo` + composição.

### 7.4 N+1 e derreferência LAZY em `EdicaoResponse.of` — **P2**
- `listarTodos`/`listarPorStatus`/`obterStatus` fazem 2 `countByEdicaoIdAndStatus` por edição (`:98-99, 108-109, 120-121`); `EdicaoResponse.of` acessa `getFotografo().getNome()`/`getEditor().getNome()` (`:29-32`) → query LAZY por item.
- **Solução**: `@Query` com `COUNT` agrupado + `LEFT JOIN FETCH`; counts podem vir em uma projeção.

### 7.5 Dois fluxos de publicação duplicados — **RESOLVIDO** ✅
- `PublicacaoService.publicar(tipo)` unifica ECOMMERCE/LOJA via Strategy Pattern.

### 7.6 Exceções duplicadas — **RESOLVIDO** ✅
- Hierarquia `EdicaoBusinessException` base + 4 subclasses.
- `GlobalExceptionHandler` com handler unificado.

### 7.7 ZIP com erro engolido — **RESOLVIDO** ✅
- Controller agora propaga `IOException`; `EdicaoZipService.limparZipsAntigos` remove ZIPs >1h.

### 7.8 `reordenarFotos` sem tipo seguro — **RESOLVIDO** ✅
- Record `ReordenarFotoRequest(id, ordem)` + `@Valid`.

### 7.9 `ZipJobResponse` morto — **RESOLVIDO** ✅
- Removido.

### 7.10 DTOs manuais — **RESOLVIDO** ✅
- `EdicaoMapper` (MapStruct) substitui `static of()` em `EdicaoResponse` e `FotoEdicaoResponse`.

### 7.11 `getCurrentUser()` duplicado e N+1 — **P3**
- Idêntico ao padrão usado em outros módulos; busca `User` a cada upload.
- **Solução**: resolver fotógrafo via claims do JWT (sem query) ou `@AuthenticationPrincipal`.

## 8. Arquivos Criados/Modificados (Refactor Fase 2)

### Novos
- `service/EdicaoQueryService.java` — Facade read-only
- `service/RawUploadService.java` — Upload RAW
- `service/EdicaoUploadEditadasService.java` — Upload editadas
- `service/PublicacaoService.java` — Publicação unificada (Strategy)
- `service/EdicaoRevisaoService.java` — Revisão (Command)
- `service/FotoEdicaoProcessor.java` — Watermark/thumbnail (Template Method)
- `service/EdicaoZipService.java` — Geração de ZIPs
- `api/EdicaoMapper.java` — MapStruct
- `api/ReordenarFotoRequest.java` — DTO tipado
- `api/ObservacoesRequest.java` — DTO tipado
- `exception/EdicaoBusinessException.java` — Base de exceções

### Modificados
- `model/StatusEdicao.java` — +State Pattern (transições validadas)
- `model/StatusFotoEdicao.java` — -`EM_EDICAO` (valor morto removido)
- `service/EdicaoService.java` — Reduzido de 534→~190 linhas (orquestrador)
- `api/EdicaoController.java` — Tipado, IOException propagada
- `api/EdicaoResponse.java` — Sem `static of()` (usa Mapper)
- `api/FotoEdicaoResponse.java` — Sem `static of()` (usa Mapper)

### Removidos
- `api/ZipJobResponse.java` — Código morto