# Módulo: Foto

## 1. Responsabilidade
Gerencia as fotos do ensaio pós-edição — upload, processamento de imagem (watermark + thumbnail), ordenação, metadados, visibilidade e status. É o repositório final das fotos exibidas na galeria do e-commerce. Consome eventos do módulo edicao para criar/remover FotoEnsaio sem escrita cross-module.

## 2. Estrutura (pós-refactor)
```
foto/
├── model/
│   ├── FotoEnsaio.java           # Entidade JPA: agendamentoId, 3 paths, status, ordem, tags, metadados, compraExtraId, fotoEdicaoId, visivel
│   └── StatusFoto.java           # Enum + State Pattern (transições validadas)
├── repository/
│   └── FotoEnsaioRepository.java # JpaRepository + @Query (countSelecionadasPacote, countPagas), findPublicadasVisiveis
├── service/
│   ├── FotoService.java            # Upload, deletar, publicar, metadados, visibilidade, status, substituir
│   └── FotoProcessingHelper.java   # Watermark + thumbnail com fallback e log (DRY)
├── api/
│   ├── FotoController.java         # @RequestMapping("/api/v1/agendamentos/{agendamentoId}/fotos")
│   ├── FotoEnsaioResponse.java     # Record DTO (sem static of())
│   ├── FotoMapper.java             # MapStruct mapper (toResponse, toPublicResponse)
│   └── FotoMetadataRequest.java    # Record: titulo, descricao, tags, categoria, dataSessao, destaque
├── acl/
│   └── AgendamentoReadService.java # Porta ACL — desacoplamento do módulo agenda
├── event/
│   ├── FotoEdicaoPublicadaEvent.java   # Evento: foto editada criada como FotoEnsaio
│   └── FotoEdicaoRemovidaEvent.java    # Evento: FotoEnsaio INEDITA removida
├── listener/
│   ├── FotoEcommerceEventListener.java  # Escuta eventos ecommerce (seleção, compra, download)
│   └── FotoEdicaoEventListener.java     # Escuta eventos edicao (criação/remoção de FotoEnsaio)
└── exception/
    ├── FotoEnsaioNaoEncontradaException.java      # 404
    ├── FotoNaoPertenceAoAgendamentoException.java  # 403
    ├── AgendamentoNaoPermitidoParaUploadException.java # 422
    └── StatusFotoInvalidoException.java            # 409
```

## 3. Dependências Externas

### Módulos internos importados
| Módulo | Uso | Tipo |
|--------|-----|------|
| **agenda** | `AgendamentoReadService` (porta ACL) — verificar status para upload | leitura (via porta) |
| **shared** | `AuditInfo` (embedded), `FileStorageService` (salvar/deletar arquivos), `ImageProcessingService` (processamento de imagem) | infraestrutura |

### Módulos que dependem deste
| Módulo | Uso |
|--------|-----|
| **edicao** | `FotoEdicaoPublicadaEvent`, `FotoEdicaoRemovidaEvent`, `FotoProcessingHelper` — publica eventos para criar/remover FotoEnsaio |
| **ecommerce** | `FotoEnsaio`, `FotoEnsaioRepository`, `FotoMapper`, `FotoEnsaioResponse` |

### Eventos consumidos
| Evento | Módulo | Ação |
|--------|--------|------|
| `FotoEdicaoPublicadaEvent` | edicao | Cria FotoEnsaio com watermark + thumbnail |
| `FotoEdicaoRemovidaEvent` | edicao | Remove FotoEnsaio INEDITA |
| `CompraExtraFotosAssociadasEvent` | ecommerce | Vincula compraExtraId às fotos |
| `CompraExtraCanceladaEvent` | ecommerce | Desvincula compra e restaura status PUBLICADA |
| `CompraExtraPagaEvent` | ecommerce | Marca fotos como PAGA |
| `FotosSelecionadasEvent` | ecommerce | Atualiza seleção no pacote |
| `FotoDownloadEvent` | ecommerce | Registra data de download |

### Eventos publicados
Nenhum.

## 4. Design Patterns Aplicados

| Pattern | Onde | Justificativa |
|---------|------|---------------|
| **State Pattern** | `StatusFoto.podeTransicionarPara()` | Centraliza regras de transição no enum, elimina if espalhado |
| **MapStruct Mapper** | `FotoMapper` | Elimina mapeamento manual, segue padrão do projeto |
| **DTO Mapper** | `FotoEnsaioResponse` (record puro) | Separação entidade/contrato da API |
| **Anti-Corruption Layer** | `AgendamentoReadService` (porta) | Inverte dependência foto→agenda, elimina importação direta de repository |
| **Domain Events** | `FotoEdicaoPublicadaEvent`, `FotoEdicaoRemovidaEvent` | Elimina escrita cross-module do edicao em FotoEnsaio |
| **Event Listener** | `FotoEdicaoEventListener`, `FotoEcommerceEventListener` | Consumidores de eventos Spring para desacoplamento |
| **Template Method + DRY** | `FotoProcessingHelper` | Centraliza processamento de imagem duplicado (2 cópias eliminadas) |

## 5. Fluxos Principais

### Fluxo 1: Upload de Fotos (Admin/Edição)
`POST /api/v1/agendamentos/{agendamentoId}/fotos` → `FotoService.uploadFotos()`:
1. Valida status via `AgendamentoReadService.isStatusPermitidoParaUpload()` (ACL).
2. Para cada arquivo: salva em `uploads/{agendamentoId}/orig/`, processa via `FotoProcessingHelper` (watermark 0.35 + thumbnail 300×200) com fallback.
3. Cria `FotoEnsaio` `INEDITA`, `visivel=true`, `ordem = count + i`.

### Fluxo 2: Publicação e Ordenação
- `PATCH /publicar` → `publicar()`: muda todas as fotos para `PUBLICADA`.

### Fluxo 3: Gestão de Metadados/Visibilidade/Status
- `PATCH /{fotoId}/metadata` → `atualizarMetadata()`: título, descrição, tags, categoria, dataSessao, destaque.
- `PATCH /{fotoId}/visibilidade` → `alterarVisibilidade()`: valida pertence ao agendamento.
- `PATCH /{fotoId}/status` → `alterarStatus()`: altera status individual.
- `PUT /{fotoId}/imagem` → `substituirImagem()`: deleta 3 arquivos antigos, up original, regera via `FotoProcessingHelper`.

### Fluxo 4: Criação via Evento (Edicao → Foto)
1. `EdicaoRevisaoService.revisarFoto()` publica `FotoEdicaoPublicadaEvent`.
2. `FotoEdicaoEventListener.handleFotoEdicaoPublicada()` cria `FotoEnsaio` com watermark + thumbnail.
3. `FotoEdicaoProcessor.processar()` delega para `FotoProcessingHelper`.

## 6. Regras Específicas
1. **Três versões por foto** (`originalPath`, `watermarkedPath`, `thumbPath`) geradas no upload.
2. **Fallback com log**: falha em watermark/thumbnail usa o path original e registra `log.warn`.
3. **`FotoEnsaioResponse`** não contém métodos estáticos — mapeamento via `FotoMapper`.
4. **StatusFoto** com transições validadas via State Pattern.
5. **Tags `@ElementCollection`** sem `orphanRemoval` (limitação JPA).

## 7. Dívidas Técnicas — Status (pós-refactor)

### 7.1 Depêndencia inversa foto → agenda — **RESOLVIDO** ✅
- Criada porta `AgendamentoReadService` + adapter `AgendamentoReadServiceAdapter`.
- `FotoService` depende da porta, não do repository do agenda.

### 7.2 Exposição da entidade JPA — **PARCIALMENTE RESOLVIDO** ◐
- Service retorna entidade (para módulos internos que precisam dos paths).
- Controller usa `FotoMapper` para mapear para DTO.
- Pendente: service retornar DTO diretamente em todos os métodos.

### 7.3 `RuntimeException` genéricas — **RESOLVIDO** ✅
- `FotoEnsaioNaoEncontradaException` (404), `FotoNaoPertenceAoAgendamentoException` (403),
  `AgendamentoNaoPermitidoParaUploadException` (422), `StatusFotoInvalidoException` (409).

### 7.4 Publicação por escrita direta (duplicação edicao) — **RESOLVIDO** ✅
- `PublicacaoService` e `EdicaoRevisaoService` publicam eventos.
- `FotoEdicaoEventListener` cria/remove `FotoEnsaio`.

### 7.5 Herança `BaseEntity` → composição — **RESOLVIDO** ✅ (global)
- `@Embeddable AuditInfo` + composição.

### 7.6 Fallback silencioso em processamento de imagem — **RESOLVIDO** ✅
- `FotoProcessingHelper` registra `log.warn` em caso de falha.

### 7.7 `deleteArquivo` sem log — **RESOLVIDO** ✅
- `deletarArquivo()` registra `log.warn` com stack trace.

### 7.8 `Tags` sem `orphanRemoval` — **PENDENTE** (limitação JPA `@ElementCollection`)

### 7.9 `atualizarOrdem` sem endpoint — **PENDENTE** (código mantido, endpoint não exposto)

### 7.10 DTOs manuais e URL hardcoded — **RESOLVIDO** ✅
- `FotoMapper` (MapStruct) substitui `static of()`.
