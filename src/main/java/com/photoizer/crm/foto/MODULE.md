# Módulo: Foto

## 1. Responsabilidade
Gerencia as fotos do ensaio — upload, processamento de imagem (watermark + thumbnail), ordenação, metadados, visibilidade e status. É o repositório final das fotos exibidas na galeria do e-commerce (pipeline Fotos, o módulo `edicao` foi removido).

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
│   └── AgendamentoReadService.java # Porta ACL — status permitido p/ upload e publicação
├── listener/
│   └── FotoEcommerceEventListener.java  # Escuta eventos ecommerce (seleção, compra, download)
└── exception/
    ├── FotoEnsaioNaoEncontradaException.java      # 404
    ├── FotoNaoPertenceAoAgendamentoException.java  # 403
    ├── AgendamentoNaoPermitidoParaUploadException.java # 422
    ├── AgendamentoNaoPermitidoParaPublicacaoException.java # 422
    └── StatusFotoInvalidoException.java            # 409
```

## 3. Dependências Externas

### Módulos internos importados
| Módulo | Uso | Tipo |
|--------|-----|------|
| **agenda** | `AgendamentoReadService` (porta ACL) — verificar status para upload/publicação | leitura (via porta) |
| **shared** | `AuditInfo` (embedded), `FileStorageService` (salvar/deletar arquivos), `ImageProcessingService` (processamento de imagem) | infraestrutura |

### Módulos que dependem deste
| Módulo | Uso |
|--------|-----|
| **ecommerce** | `FotoEnsaio`, `FotoEnsaioRepository`, `FotoMapper`, `FotoEnsaioResponse` |

### Eventos consumidos
| Evento | Módulo | Ação |
|--------|--------|------|
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
| **Event Listener** | `FotoEcommerceEventListener` | Consumidor de eventos Spring para desacoplamento |
| **Template Method + DRY** | `FotoProcessingHelper` | Centraliza processamento de imagem (watermark + thumbnail) |

## 5. Fluxos Principais

### Fluxo 1: Upload de Fotos (Admin)
`POST /api/v1/agendamentos/{agendamentoId}/fotos` → `FotoService.uploadFotos()`:
1. Valida status via `AgendamentoReadService.isStatusPermitidoParaUpload()` (ACL): permitido apenas **após o pagamento final** (`EM_EDICAO`, `FOTOS_ENVIADAS_PARA_SELECAO`, `FOTOS_ENTREGUES`, `FINALIZADO`).
2. Para cada arquivo: salva em `uploads/{agendamentoId}/orig/`, processa via `FotoProcessingHelper` (watermark 0.35 + thumbnail 300×200) com fallback.
3. Cria `FotoEnsaio` `INEDITA`, `visivel=true`, `ordem = count + i`.

### Fluxo 2: Publicação e Ordenação
- `PATCH /publicar` → `publicar()`: valida status pago (`isStatusPermitidoParaPublicacao`) e muda todas as fotos `INEDITA` para `PUBLICADA` (publicação **manual** via botão "Publicar Galeria").

### Fluxo 3: Gestão de Metadados/Visibilidade/Status
- `PATCH /{fotoId}/metadata` → `atualizarMetadata()`: título, descrição, tags, categoria, dataSessao, destaque.
- `PATCH /{fotoId}/visibilidade` → `alterarVisibilidade()`: valida pertence ao agendamento.
- `PATCH /{fotoId}/status` → `alterarStatus()`: altera status individual; transição para `PUBLICADA` exige status pago.
- `PUT /{fotoId}/imagem` → `substituirImagem()`: deleta 3 arquivos antigos, up original, regera via `FotoProcessingHelper`.

## 6. Regras Específicas
1. **Upload e publicação liberados após o pagamento final** (`EM_EDICAO`…`FINALIZADO`), validado via porta ACL tanto no upload quanto na publicação.
2. **Publicação manual**: botão "Publicar Galeria" (`PATCH /publicar`); sem publicação automática.
3. **Três versões por foto** (`originalPath`, `watermarkedPath`, `thumbPath`) geradas no upload.
4. **Fallback com log**: falha em watermark/thumbnail usa o path original e registra `log.warn`.
5. **`FotoEnsaioResponse`** não contém métodos estáticos — mapeamento via `FotoMapper`.
6. **StatusFoto** com transições validadas via State Pattern.
7. **Tags `@ElementCollection`** sem `orphanRemoval` (limitação JPA).

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

### 7.4 Integração com edicao — **REMOVIDA**
- O módulo `edicao` foi removido; a pipeline oficial passou a ser o upload direto do `foto` (`AdminGaleriaPage`). Eventos/listener de integração (`FotoEdicaoPublicadaEvent`, `FotoEdicaoRemovidaEvent`, `FotoEdicaoEventListener`) foram removidos.

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
