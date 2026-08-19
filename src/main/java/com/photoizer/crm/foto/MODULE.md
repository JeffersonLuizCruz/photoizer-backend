# Módulo: Foto

## 1. Responsabilidade
Gerencia as fotos do ensaio pós-edição — upload, processamento de imagem (watermark + thumbnail), ordenação, metadados, visibilidade e status. É o repositório final das fotos exibidas na galeria do e-commerce. Contém também `ImageProcessingService` (Thumbnailator + imagem AWT), usado por este módulo e pelo módulo `edicao`.

## 2. Estrutura
```
foto/
├── model/
│   ├── FotoEnsaio.java         # Entidade JPA (extends BaseEntity): agendamentoId, 3 paths, status, ordem, tags, metadados, compraExtraId, fotoEdicaoId, visivel
│   └── StatusFoto.java         # Enum: INEDITA, PUBLICADA, AGUARDANDO_COMPROVANTE, AGUARDANDO_CONFIRMACAO, PAGA
├── repository/
│   └── FotoEnsaioRepository.java # JpaRepository + @Query (countSelecionadasPacote, countPagas), findPublicadasVisiveis
├── service/
│   ├── FotoService.java            # 211 linhas: upload, deletar, publicar, ordenar, metadados, visibilidade, status, substituir
│   └── ImageProcessingService.java # 76 linhas: Thumbnailator (300×200) + watermark com redimensionamento
└── api/
    ├── FotoController.java         # @RequestMapping("/api/v1/agendamentos/{agendamentoId}/fotos")
    ├── FotoEnsaioResponse.java     # Record + static of() e ofPublic() (ofPublic oculta original/EXIF)
    └── FotoMetadataRequest.java    # Record: titulo, descricao, tags, categoria, dataSessao, destaque
```

## 3. Dependências Externas

### Módulos internos importados
| Módulo | Uso |
|--------|-----|
| **agenda** | `Agendamento`, `AgendamentoRepository`, `StatusAgendamento`, `EnsaioNaoFinalizadoException` — usado para validar status antes do upload |
| **shared** | `BaseEntity` (herança), `FileStorageService` (salvar/deletar arquivos) |

### Módulos que dependem deste
| Módulo | Uso |
|--------|-----|
| **edicao** | `FotoEnsaio`, `FotoEnsaioRepository`, `StatusFoto`, `ImageProcessingService` — **escreve `FotoEnsaio` diretamente** (publicação/revisão) |
| **ecommerce** | `FotoEnsaio`, `FotoEnsaioRepository`, `FotoService`, `FotoEnsaioResponse` |

### Eventos
Não publica nem consome eventos (apesar de fotos serem o artefato central da publicação — a publicação é feita por **escrita direta** do módulo `edicao`, ver dívida 7.4).

## 4. Fluxos Principais

### Fluxo 1: Upload de Fotos (Admin/Edição)
`POST /api/v1/agendamentos/{agendamentoId}/fotos` → `FotoService.uploadFotos()` (`FotoService.java:63-112`):
1. Valida agendamento em `STATUS_ALLOW_UPLOAD` (`EM_EDICAO`, `SELECAO_DAS_FOTOS`, `FOTOS_ENVIADAS_PARA_SELECAO`, `FOTOS_ENTREGUES`, `FINALIZADO`) senão `EnsaioNaoFinalizadoException`.
2. Para cada arquivo: salva em `uploads/{agendamentoId}/orig/`, gera watermark (`wm_`, texto `© Photoizer Studio`, opacidade 0.35) e thumbnail (`thumb_`, 300×200) via `ImageProcessingService` com fallback ao original em caso de erro.
3. Cria `FotoEnsaio` `INEDITA`, `visivel=true`, `ordem = count + i`.

### Fluxo 2: Publicação e Ordenação
- `PATCH /publicar` → `publicar()` (`:122-128`): muda **todas** as fotos para `PUBLICADA`.
- `atualizarOrdem(id, ordem)` (`:130-134`): seta `ordem` individual (sem endpoint REST exposto — provável código morto no controller atual).

### Fluxo 3: Gestão de Metadados/Visibilidade/Status
- `PATCH /{fotoId}/metadata` → `atualizarMetadata()` (`:140-149`): título, descrição, tags (`ArrayList` substitui a lista), categoria, `dataSessao`, `destaque` — condicional por campo.
- `PATCH /{fotoId}/visibilidade` → `alterarVisibilidade()` (`:151-158`): valida pertence ao agendamento.
- `PATCH /{fotoId}/status` → `alterarStatus()` (`:160-167`): altera status individual.
- `PUT /{fotoId}/imagem` → `substituirImagem()` (`:169-204`): deleta 3 arquivos antigos, up original, regera watermark+thumbnail.

### Fluxo 4: Servir arquivos
- `GET /{fotoId}/original`, `/{fotoId}/watermarked`, `/{fotoId}/thumb` → `servirOriginal/servirWatermarked/servirThumb` no controller.

## 5. Regras Específicas
1. **Três versões por foto** (`originalPath`, `watermarkedPath`, `thumbPath`) geradas no upload; watermark redimensiona para máx. 1600px (proteção contra uso sem pagamento).
2. **Fallback silencioso**: falha em watermark/thumbnail usa o path original (não registra log).
3. **`deletarArquivo()` engole `IOException`** (`FotoService.java:206-210`).
4. **`FotoEnsaioResponse.ofPublic()`** oculta `originalUrl` e `metadataExif` (segurança para a galeria pública).
5. **Tags `@ElementCollection`** sem `orphanRemoval` — trocar a lista por `new ArrayList<>(...)` substitui a coleção, mas a versão antiga (removida) não é marcada para deleção.
6. **`alterarVisibilidade`/`alterarStatus`/`substituirImagem`** recebem `agendamentoId` + `fotoId` e validam pertencimento com `RuntimeException` genérica.

## 6. Testes
Nenhum teste específico para este módulo. Apenas `CrmApplicationTests` (smoke de contexto).

## 7. Dívidas Técnicas e Melhorias Recomendadas

### 7.1 Depêndencia inversa foto → agenda e exceção cruzada — **P1**
- `FotoService` importa `Agendamento`, `AgendamentoRepository`, `StatusAgendamento` e **`agenda.exception.EnsaioNaoFinalizadoException`** (`FotoService.java:3-6, 40`) para validar um requisito do próprio módulo (fazer upload só após o ensaio estar apto).
- **Solução**: colocar a regra de aptidão no próprio domínio (enum `StatusAgendamento` na agenda + evento de mudança de status), ou passar um boolean derivado; mover exceção para `shared` (hierarquia central).

### 7.2 Exposição da entidade JPA — **P1**
- `listar()`, `buscarPorId()`, `uploadFotos()`, `publicar()`, `atualizarOrdem()`, `atualizarMetadata()`, `alterarVisibilidade()`, `alterarStatus()`, `substituirImagem()` **retornam e recebem `FotoEnsaio`** como contrato público do service/API; o controller mapeia `FotoEnsaioResponse.of(...)`.
- **Solução**: service retorna DTOs (`FotoEnsaioResponse` com MapStruct); entidade restrita ao repository/domínio.

### 7.3 `RuntimeException` genéricas — **P1**
- `buscarPorId` (`FotoService.java:60`), `uploadFotos` (`:65`), `alterarVisibilidade/alterarStatus/substituirImagem` (`:154, 163, 172`) lançam `RuntimeException` → 500 em vez de 404/4xx.
- **Solução**: `FotoNaoEncontradaException` ou hierarquia central `BusinessException`/`NotFoundException`.

### 7.4 Publicação por escrita direta (duplicação edicao) — **[CRÍTICO] P1**
- O módulo **edicao** cria/altera `FotoEnsaio` diretamente (`publicarNoEcommerce`, `revisarFoto`, `publicarLoja`) duplicando a lógica de watermark+thumbnail deste `FotoService`. Há 3 cópias da mesma geração de imagem (FotoService.uploadFotos, FotoService.substituirImagem, EdicaoService).
- **Solução**: padronizar a publicação via `FotosPublicadasEvent` consumido pelo módulo foto (única fábrica de `FotoEnsaio`); mover `ImageProcessingService` para `shared` (infraestrutura) ou mantê-lo como serviço de domínio deste módulo com método único `criarFotoDeEnsaio(...)`.

### 7.5 Herança `BaseEntity` → composição — **P1** (padrão-aplicável)
- `FotoEnsaio` estende `@MappedSuperclass`.
- **Solução**: `@Embeddable AuditInfo` + Auditing; eliminar `BaseEntity`/`@SuperBuilder`.

### 7.6 Fallback silencioso em processamento de imagem — **P2**
- Em caso de falha de watermark/thumbnail usa o original sem log (`FotoService.java:83-94, 183-196`).
- **Solução**: ao menos `log.warn`; decidir se falha deve abortar o upload (consistência) ou degradar.

### 7.7 `deleteArquivo` sem log e sem exceção — **P2**
- `deletarArquivo()` (`.java:206-210`) engole `IOException`.
- **Solução**: logar e reportar inconsistência (arquivo órfão) sem quebrar a transação — ou tratar como alerta.

### 7.8 `Tags` sem `orphanRemoval + CascadeType` — **P3**
- **Solução**: `@ElementCollection(fetch = LAZY)` com `orphanRemoval = true`; preferir entidade `Tag` em `@ManyToMany` se for filtrar por tag no banco.

### 7.9 `atualizarOrdem` sem endpoint e lógica pública em lote não transacional — **P3**
- `publicar()` seta `PUBLICADA` em lote sem emitir evento e sem validação de destaque; endpoint `/publicar` e rota `PATCH /{fotoId}/visibilidade`/`status` aceitam parametrização solta.
- **Solução**: transição de status via método de domínio com validação (ex.: `INEDITA→PUBLICADA`); expor reordenação com request tipado.

### 7.10 DTOs manuais e URL hardcoded — **P2**
- `FotoEnsaioResponse.of()`/`ofPublic()` escritos à mão; URLs `/api/v1/agendamentos/...` e `/api/v1/ecommerce/fotos/...` concatenadas em strings.
- **Solução**: MapStruct; gerar URLs via helper central (`UrlProvider`) para evitar divergência de contratos.

## 8. Exemplos de arquivos afetados
- `FotoService.java:3-6,40` — dependência de `agenda` (repo + exceção); `:53-55` expõe entidade; `:60,65,154,163,172` — `RuntimeException`; `:83-94,183-196` — fallback silencioso; `:206-210` — deleta arquivo sem log.
- `FotoController.java:38-142` — expõe entidades via service.
- `FotoEnsaio.java:31` — herança `BaseEntity`; `FotoEnsaioResponse.java:33-87` — DTOs manuais.
- `module edicao` — `EdicaoService.java:267-330, 342-404, 406-453` escrevem em `FotoEnsaio` (escrita cross-module).