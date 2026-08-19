# Módulo: Edição

## 1. Responsabilidade
Gerencia o processo de edição de fotos do ensaio desde o upload das fotos RAW (fotógrafo) até a edição (editor), revisão individual, conclusão e publicação no e-commerce/loja. É ativado automaticamente quando o pagamento final do agendamento é registrado (via evento). Inclui também download em ZIP de RAW/editadas.

## 2. Estrutura
```
edicao/
├── model/
│   ├── Edicao.java              # Entidade JPA (extends BaseEntity): agendamentoId, status, fotografo (@ManyToOne User), editor, dataEnvioRaw, dataEnvioEditado, observacoes
│   ├── FotoEdicao.java          # Entidade JPA (extends BaseEntity): edicaoId, rawPath, rawFileName, editedPath, editedFileName, status, ordem, aprovado, comentario
│   ├── StatusEdicao.java        # Enum: AGUARDANDO_RAW, RAW_ENVIADOS, EM_EDICAO, EDICAO_CONCLUIDA
│   └── StatusFotoEdicao.java    # Enum: RAW, EM_EDICAO, EDITADO (EM_EDICAO nunca atribuído no código)
├── repository/
│   ├── EdicaoRepository.java    # JpaRepository + findByAgendamentoId, existsByAgendamentoId, findAllByOrderByUpdatedAtDesc, findByStatusOrderByUpdatedAtDesc
│   └── FotoEdicaoRepository.java # JpaRepository + findByEdicaoIdOrderByOrdemAsc, countByEdicaoId, countByEdicaoIdAndStatus, findByEdicaoIdAndStatus
├── service/
│   └── EdicaoService.java       # ~534 linhas: upload raw, upload editadas, concluir, publicar (2 fluxos), revisão, zips, reordenar
├── api/
│   ├── EdicaoController.java    # ~233 linhas: 16 endpoints REST
│   ├── EdicaoResponse.java      # Record + static of(): id, agendamentoId, status, fotografo, editor, datas, contagens
│   ├── FotoEdicaoResponse.java  # Record + static of(): id, ordem, nomes, URLs, status, aprovado, comentario
│   ├── RevisaoRequest.java      # Record @Valid: aprovado (Boolean), comentario
│   └── ZipJobResponse.java      # Record NÃO UTILIZADO (código morto)
├── event/
│   ├── RawEnviadosEvent.java    # Publicado ao enviar RAW: agendamentoId, quantidade
│   ├── EdicaoConcluidaEvent.java# Publicado ao concluir edição: agendamentoId
│   └── FotosPublicadasEvent.java# Publicado ao publicar fotos: agendamentoId, quantidade
├── listener/
│   └── EdicaoListener.java      # Cria Edicao automaticamente ao receber PagamentoFinalRegistradoEvent
└── exception/
    ├── EdicaoNaoEncontradaException.java       # RuntimeException
    ├── FotoEdicaoNaoEncontradaException.java   # RuntimeException
    ├── FotoSemRawException.java                # RuntimeException
    └── StatusEdicaoInvalidoException.java      # RuntimeException
```

## 3. Dependências Externas

### Módulos internos importados — **[VIOLAÇÕES Modulith]**
| Módulo | Uso | Tipo |
|--------|-----|------|
| **agenda** | `Agendamento`, `AgendamentoRepository`, `StatusAgendamento` — **service muta status do agendamento** em `uploadRaw`/`publicarNoEcommerce`/`publicarLoja` | entrada **e escrita** |
| **auth** | `User`, `UserRepository` — `getCurrentUser()` no upload RAW para vincular fotógrafo | leitura |
| **foto** | `FotoEnsaio`, `FotoEnsaioRepository`, `StatusFoto`, `ImageProcessingService` — **cria/atualiza/remove `FotoEnsaio`** diretamente (publicação e revisão) | entrada **e escrita** |
| **shared** | `BaseEntity` (herança), `FileStorageService` (salvar/deletar arquivos) | infraestrutura |

> A boa prática está presente: `EdicaoListener` **consome eventos** (`PagamentoFinalRegistradoEvent`). As violações estão no `EdicaoService`, que **escreve em entidades de outros módulos** (agenda e foto) diretamente.

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

## 4. Fluxos Principais

### Fluxo 1: Criação automática da Edição
1. `PagamentoFinalRegistradoEvent` publicado pelo módulo `agenda`.
2. `EdicaoListener.handlePagamentoFinal()`: cria `Edicao` `AGUARDANDO_RAW` se `!existsByAgendamentoId` — **único ponto de escuta via evento**.

### Fluxo 2: Upload RAW (Fotógrafo)
`POST /api/v1/edicao/{agendamentoId}/raw` → `uploadRaw()` (`EdicaoService.java:150-200`):
1. Valida agendamento `EM_EDICAO` ou `AGUARDANDO_PAGAMENTO_FINAL`, senão `StatusEdicaoInvalidoException`.
2. Cria `Edicao` lazy se inexistente (`status = AGUARDANDO_RAW`).
3. Salva cada arquivo em `uploads/{agendamentoId}/raw/`, cria `FotoEdicao.RAW` com `ordem = count + i`.
4. Seta `RAW_ENVIADOS` + `dataEnvioRaw`; vincula fotógrafo via `getCurrentUser()` (N+1: 1 query `User` por chamada).
5. **Efeito colateral cross-module**: se agendamento `AGUARDANDO_PAGAMENTO_FINAL`, muta status do `Agendamento` para `EM_EDICAO`.
6. Publica `RawEnviadosEvent`.

### Fluxo 3: Upload Editadas (Editor)
`POST /agendamentoId/editadas` → `uploadEditadas()` (`EdicaoService.java:202-243`):
1. Rejeita se `AGUARDANDO_RAW`.
2. **Match por nome de arquivo**: vincula editada à RAW por `rawFileName == originalFilename`; nome sem RAW → `FotoSemRawException`.
3. Seta `EDITADO` + `editedPath`/`editedFileName`; `Edicao` → `EM_EDICAO`.

### Fluxo 4: Revisão (Aprovação Individual)
`PATCH /fotos/{fotoId}/revisao` → `revisarFoto()` (`EdicaoService.java:342-404`):
- `aprovado=true` + `editedPath`: gera watermark + thumbnail, cria `FotoEnsaio.INEDITA` (escrita no módulo **foto**).
- `aprovado=false`: deleta `FotoEnsaio.INEDITA` existente.
- **Duplica a lógica de watermark/thumbnail de `publicarNoEcommerce`** (mesmo bloco try/catch).

### Fluxo 5: Conclusão e Publicação
1. `PATCH /concluir` → `concluirEdicao()` (`:245-265`): exige ≥1 editada, seta `EDICAO_CONCLUIDA`, publica `EdicaoConcluidaEvent`.
2. `PATCH /publicar` → `publicarNoEcommerce()` (`:267-330`): valida `EDICAO_CONCLUIDA`; para cada editada gera watermark+thumbnail, cria `FotoEnsaio.PUBLICADA` (**escrita em foto**), avança agendamento → `SELECAO_DAS_FOTOS` (**escrita em agenda**), publica `FotosPublicadasEvent`.
3. `PATCH /publicar-loja` → `publicarLoja()` (`:406-453`): publica as `INEDITA` → `PUBLICADA`; lógica **sobreposta** a `publicarNoEcommerce` (2 caminhos para o mesmo destino).

### Fluxo 6: Zips e reordenação
- `GET /download-raw`/`download-editadas` → `gerarZipRaw`/`gerarZipEditadas` (`:473-523`): cria ZIPs em `uploads/temp/`, limpa apenas ZIPs de mesmo prefixo; `catch (Exception)` no controller engole erro com 500 genérico.
- `PATCH /fotos/reordenar` → `reordenarFotos()` (`:456-471`): recebe `List<Map<String,Object>>` — **sem tipo seguro**, parsing manual de `id`/`ordem`.

## 5. Regras Específicas
1. **Dois endpoints de publicação** (`publicarNoEcommerce` e `publicarLoja`) com lógicas similares, um criando `FotoEnsaio` do zero e o outro reaproveitando `INEDITA` da revisão.
2. **Ligação arquivo↔RAW por nome** — frágil: usuário precisa renomear arquivo editado idêntico ao RAW.
3. **Watermark + thumbnail** via `ImageProcessingService` (texto `"© Photoizer Studio"`, opacidade 15%) com fallback ao caminho original em caso de erro.
4. **`getCurrentUser()`** lê `SecurityContext` e busca `UserRepository` a cada `uploadRaw`; retorna `null` para anonymous (fotografo não vinculado).
5. **Status machine parcialmente validada**: `uploadRaw/concluir/publicar` têm `if`s manuais, mas não há estado central — `EM_EDICAO` (da `FotoEdicao`) nunca é atribuído (valor morto do enum).

## 6. Testes
Nenhum teste específico para este módulo. Apenas `CrmApplicationTests` (smoke de contexto).

## 7. Dívidas Técnicas e Melhorias Recomendadas

### 7.1 Escrita cross-module em `agenda` e `foto` — **[CRÍTICO] P1**
- `EdicaoService.uploadRaw()` (`:192-195`) e `publicarNoEcommerce`/`publicarLoja` (`:322-325, 441-444`) **mutam `Agendamento.status`**; `revisarFoto`/`publicar` **criam/excluem/atualizam `FotoEnsaio`** (`:352-401, 416-439`).
- **Solução**: publicação deveria acontecer via eventos (`FotosPublicadasEvent` já existe mas é reservado) com listener no módulo `foto` criando as `FotoEnsaio`; transição do agendamento via evento `EdicaoConcluidaEvent` consumido pelo módulo `agenda` (que já é dono do status machine). Manter `ImageProcessingService` como serviço de infraestrutura independente.

### 7.2 `EdicaoService` oversized (~534 linhas, 18 métodos) — **P1**
- Mistura orquestração de arquivos, status machine, revisão, publicação e ZIP em um único bean.
- **Solução**: splits responsabilidade — `RawUploadService`, `PublicacaoFotoService`, `ZipperService`, `RevisaoService` (ou `EdicaoApplicationService` + especialistas de domínio).

### 7.3 Herança `BaseEntity` → composição — **P1** (padrão-aplicável)
- `Edicao`/`FotoEdicao` estendem `@MappedSuperclass`.
- **Solução**: `@Embeddable AuditInfo` + Auditing; eliminar `BaseEntity`/`@SuperBuilder`; enums nas entidades (`status` já é `@Enumerated(STRING)`).

### 7.4 N+1 e derreferência LAZY em `EdicaoResponse.of` — **P2**
- `listarTodos`/`listarPorStatus`/`obterStatus` fazem 2 `countByEdicaoIdAndStatus` por edição (`:98-99, 108-109, 120-121`); `EdicaoResponse.of` acessa `getFotografo().getNome()`/`getEditor().getNome()` (`:29-32`) → query LAZY por item.
- **Solução**: `@Query` com `COUNT` agrupado + `LEFT JOIN FETCH`; counts podem vir em uma projeção.

### 7.5 Dois fluxos de publicação duplicados — **P1**
- `publicarNoEcommerce` (`:267-330`) e `publicarLoja` (`:406-453`) + bloco watermark/thumbnail de `revisarFoto` (`:355-394') — 3 cópias da mesma lógica de geração de `FotoEnsaio`.
- **Solução**: um único método `publicarAprovadas(...)` (extrair `FotoEnsaioFactory`); decidir um só contrato de publicação.

### 7.6 Exceções duplicadas e não-centralizadas — **P2**
- 4 classes `RuntimeException` quase idênticas; mapeadas uma a uma no `GlobalExceptionHandler` (linhas 82-103).
- **Solução**: hierarquia central `BusinessException` + subclasses (`NotFoundException`, `IllegalStateException` de domínio).

### 7.7 ZIP com erro engolido e lixo acumulado — **P2**
- `downloadRawZip`/`downloadEditadasZip` (`EdicaoController.java:196-222`) usam `catch (Exception) → 500`; `limparZipsAntigos` (`:525-533`) só remove ZIPs do mesmo prefixo — acumula lixo de outras execuções.
- **Solução**: throw para o `GlobalExceptionHandler`; agendamento de limpeza (scheduler) em vez de limpar apenas por prefixo.

### 7.8 `reordenarFotos` sem tipo seguro — **P2**
- `List<Map<String,Object>>` no controller (`EdicaoController.java:188`) com parsing manual (`EdicaoService.java:457-462`).
- **Solução**: record `ReordenarFotoRequest(id, ordem)` + `@Valid`.

### 7.9 `ZipJobResponse` morto — **P3**
- Record sem uso (fluxo de ZIP é síncrono). Remover ou integrar a um fluxo assíncrono de jobs.

### 7.10 DTOs manuais e campo `status: String` — **P2**
- `EdicaoResponse.of`/`FotoEdicaoResponse.of` escritas à mão; `status` serializado como `.name()`.
- **Solução**: MapStruct com `@Mapping` para `status.name()` (ou serializar o próprio enum); expor enum nas responses.

### 7.11 `getCurrentUser()` duplicado e N+1 — **P3**
- Idêntico ao padrão usado em outros módulos; busca `User` a cada upload.
- **Solução**: resolver fotógrafo via claims do JWT (sem query) ou `@AuthenticationPrincipal`.

## 8. Exemplos de arquivos afetados
- `EdicaoService.java:150-200` — escreve em `Agendamento`; `:192-195` muta status; `:267-330` grava `FotoEnsaio`; `:342-404` revisão cria/exclui `FotoEnsaio`; `:406-453` `publicarLoja`; `:456-471` reordenar sem tipo; `:473-533` ZIPs.
- `EdicaoResponse.java:24-41` — `of()` manual + LAZY de fotógrafo/editor.
- `EdicaoController.java:196-222` — `catch (Exception)` nos downloads ZIP.
- `ZipJobResponse.java` — código morto; `StatusFotoEdicao.EM_EDICAO` — valor não utilizado.