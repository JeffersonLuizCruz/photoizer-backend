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
│   ├── RemoverFotosLoteRequest.java # Record: fotoIds (lote)
│   ├── FotosRemovidasResponse.java # Record: removidas
│   ├── FotoMapper.java             # MapStruct mapper (toResponse, toPublicResponse)
│   └── FotoMetadataRequest.java    # Record: titulo, descricao, tags, categoria, dataSessao, destaque
├── event/
│   └── FotosRemovidasEvent.java    # Notifica ecommerce para limpar carrinho/favoritos/comentários
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
| Evento | Módulo consumidor | Ação |
|--------|-------------------|------|
| `FotosRemovidasEvent` | ecommerce | Remove favoritos, itens de carrinho e comentários das fotos removidas |

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
1. Valida status via `AgendamentoReadService.isStatusPermitidoParaUpload()` (ACL): permitido apenas **após o pagamento final** (`EM_EDICAO`, `FINALIZADO`).
2. Para cada arquivo: valida o formato (`ImageIO.getImageReaders` → 422 com o nome do arquivo se ilegível), salva em `uploads/{agendamentoId}/orig/`, processa via `FotoProcessingHelper` (watermark com o nome da contratada + thumbnail 300×200 + prévia pública 1024px) com fallback.
3. Cria `FotoEnsaio` `INEDITA`, `visivel=true`, `ordem = count + i`.
4. Se a requisição falhar, a transação sofre rollback e **os arquivos gravados nesta chamada são removidos** (sem órfãos no volume).

> O frontend envia as fotos em **lotes de ~50MB/5 arquivos**; cada requisição é atômica (um arquivo inválido derruba só aquele lote). Limites: 100MB/arquivo e 200MB/request.

### Fluxo 2: Publicação e Ordenação
- `PATCH /publicar` → `publicar()`: valida status pago (`isStatusPermitidoParaPublicacao`) e muda todas as fotos `INEDITA` para `PUBLICADA` (publicação **manual** via botão "Publicar Galeria").

### Fluxo 3: Gestão de Metadados/Visibilidade/Status
- `PATCH /{fotoId}/metadata` → `atualizarMetadata()`: título, descrição, tags, categoria, dataSessao, destaque.
- `PATCH /{fotoId}/visibilidade` → `alterarVisibilidade()`: ocultar é bloqueado para fotos vinculadas a pacote, compra, download ou edição; exibir continua permitido para corrigir estado oculto.
- `PATCH /{fotoId}/status` → `alterarStatus()`: altera status individual; transição para `PUBLICADA` exige status pago e fotos protegidas não podem ter o status alterado.
- `PUT /{fotoId}/imagem` → `substituirImagem()`: bloqueado para fotos vinculadas a pacote, compra, download ou edição; deleta 4 arquivos antigos, sobe original e regera via `FotoProcessingHelper`.

### Fluxo 4: Remoção em lote
- `POST /remover-lote` → `deletarEmLote(agendamentoId, fotoIds)`:
  1. deduplica os IDs e valida existência (404) e propriedade do agendamento (403);
  2. rejeita o lote inteiro (409) se alguma foto estiver vinculada a compra, pacote ou status de compra (`PAGA`/`AGUARDANDO_*`);
  3. apaga os 4 arquivos de cada foto e remove os registros;
  4. publica `FotosRemovidasEvent` para limpeza no ecommerce.
- A remoção individual (`DELETE /{fotoId}`) reutiliza a mesma rotina e as mesmas proteções.

## 6. Regras Específicas
1. **Upload e publicação liberados após o pagamento final** (`EM_EDICAO`…`FINALIZADO`), validado via porta ACL tanto no upload quanto na publicação.
2. **Publicação manual**: botão "Publicar Galeria" (`PATCH /publicar`); sem publicação automática.
3. **Versões por foto** (`originalPath`, `watermarkedPath` 1600px, `thumbPath` 300×200 e `previewPath` para o cliente, em 800px).
4. **Fallback com log**: falha em watermark/thumbnail usa o path original e registra `log.warn`.
5. **`FotoEnsaioResponse`** não contém métodos estáticos — mapeamento via `FotoMapper`.
6. **StatusFoto** com transições validadas via State Pattern.
7. **Tags `@ElementCollection`** sem `orphanRemoval` (limitação JPA).
8. **Fotos protegidas**: remoção, alteração de status, ocultação e substituição de imagem bloqueadas (409) para fotos com `compraExtraId`, `selecionadaPacote`, `emSelecaoEdicao`, `editadaPath`, `dataDownload` ou status `PAGA`/`AGUARDANDO_*`; a remoção em lote é atômica e não apaga nada se houver foto protegida.
9. **Prévia pública protegida**: o endpoint público de watermark serve `preview_v2_*` em **800px e JPEG ~0.60**. O thumbnail público também é gerado a partir da versão com watermark (`thumb_protegido_*`). Fotos antigas (ou com cache de uma versão anterior) têm uma versão reduzida gerada sob demanda a partir do watermark já gravado (`preview_v2_{fotoId}_{versao}.jpg`), preservando a marca antiga. Ao mudar tamanho/qualidade, incremente `ImageProcessingService.PREVIEW_VERSION`.
10. **Marca d'água**: novas fotos/edições usam `© {nomeContratada}` (fallback `nomeFotografo` → `Photoizer Studio`). O texto da marca é escalado com a largura da imagem.

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

## 8. Numeração e versão editada (V8/V9)

1. **Numeração da galeria**: `numeroGaleria` é atribuído na publicação, na ordem de `ordem`, e é estável. Fotos ocultas não recebem número; fotos não publicadas também não.
2. **Backfill**: a migration V8 numera as fotos `PUBLICADA` visíveis de galerias existentes.
3. **Renumeração**: `POST /fotos/renumerar` só é permitido antes de seleção/download/compra; usa lock pessimista e índice único.
4. **Versão editada**: `uploadEditada` mantém o RAW, grava os paths editados e publica `FotoEditadaEvent`; a troca pública para `EDITADA` só ocorre no evento de publicação da seleção.
5. **Nome estável**: `fileName` permanece o nome de exibição da foto; o nome do arquivo editado fica em `editadaFileName`.
6. **Proteções**: fotos em seleção ativa (`emSelecaoEdicao`), com versão editada (`editadaPath`), vendidas ou baixadas não podem ser removidas/renumeradas. Ao publicar, `EdicaoPublicadaEvent` limpa `emSelecaoEdicao` e troca a versão para `EDITADA`.
7. **Downloads**: ZIP e download individual usam `numeroGaleria` + nome; quando `versaoAtual = EDITADA`, servem o arquivo editado. Fotos ocultas ou não publicadas não são baixáveis.
8. **Eventos consumidos**: `SelecaoEnviadaEvent`, `SelecaoCanceladaEvent`, `EdicaoPublicadaEvent`.
9. **Evento publicado**: `FotoEditadaEvent`.
