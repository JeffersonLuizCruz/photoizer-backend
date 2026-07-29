# Módulo: Foto

## 1. Responsabilidade
Gerencia as fotos do ensaio após a edição — upload, metadados, ordenação, visibilidade, status. É o repositório final das fotos que serão exibidas na galeria do e-commerce. Também contém o serviço de processamento de imagem (watermark + thumbnail).

## 2. Estrutura
```
foto/
├── model/
│   ├── FotoEnsaio.java         # Entidade JPA (extends BaseEntity): agendamentoId, 3 paths (original, watermarked, thumb), status, ordem, metadados
│   └── StatusFoto.java         # Enum: INEDITA, PUBLICADA, AGUARDANDO_PAGAMENTO, PAGA
├── repository/
│   └── FotoEnsaioRepository.java # JpaRepository: queries por agendamentoId, status, visibilidade, fotoEdicaoId
├── service/
│   ├── FotoService.java            # 211 linhas: upload, deletar, publicar, ordenar, metadados, visibilidade, substituir
│   └── ImageProcessingService.java # 60 linhas: Thumbnailator (300x200) + watermark text com opacidade
└── api/
    ├── FotoController.java         # REST: listar, upload, deletar, publicar, ordenar, metadados, visibilidade, status
    ├── FotoEnsaioResponse.java     # Record com dados completos da foto + URL da imagem com marca d'água
    └── FotoMetadataRequest.java    # Record: titulo, descricao, tags, categoria, dataSessao, destaque
```

## 3. Dependências Externas

### Módulos internos
| Módulo | Uso |
|--------|-----|
| **agenda** | `AgendamentoRepository`, `Agendamento`, `StatusAgendamento`, `EnsaioNaoFinalizadoException` |
| **shared** | `BaseEntity`, `FileStorageService` |
| **foto (próprio)** | `ImageProcessingService` |

### Módulos que dependem deste
| Módulo | Uso |
|--------|-----|
| **edicao** | `FotoEnsaioRepository`, `FotoEnsaio`, `StatusFoto`, `ImageProcessingService` |
| **ecommerce** | `FotoEnsaioRepository`, `FotoEnsaio`, `StatusFoto`, `FotoEnsaioResponse`, `FotoService` |

### Eventos
Nenhum. Módulo não publica nem consome eventos.

## 4. Fluxos Principais

### Fluxo 1: Upload de Fotos (Admin)
1. `POST /api/v1/fotos/{agendamentoId}/upload` (multipart) → `FotoService.uploadFotos()`:
   - Valida: agendamento deve estar em `EM_EDICAO`, `SELECAO_DAS_FOTOS`, `FOTOS_ENVIADAS_PARA_SELECAO`, `FOTOS_ENTREGUES` ou `FINALIZADO`
   - Para cada arquivo: salva original em `uploads/{agendamentoId}/orig/`
   - Gera watermark: `wm_{filename}` em `uploads/{agendamentoId}/orig/` (texto "© Photoizer Studio")
   - Gera thumbnail: `thumb_{filename}` (300×200, qualidade 0.7)
   - Cria `FotoEnsaio` com `status = INEDITA`, `visivel = true`

### Fluxo 2: Gerenciamento de Metadados
- `PATCH /fotos/{id}/metadados` → atualiza título, descrição, tags, categoria, dataSessao, destaque
- `PATCH /fotos/{agendamentoId}/fotos/{fotoId}/visibilidade` → altera visível true/false
- `PATCH /fotos/{agendamentoId}/fotos/{fotoId}/status` → altera status individual
- `PATCH /fotos/{id}/ordem` → atualiza ordem da foto

### Fluxo 3: Publicação em Lote
- `POST /fotos/{agendamentoId}/publicar` → muda `status` de todas as fotos do agendamento para `PUBLICADA`

### Fluxo 4: Substituição de Imagem
- `PUT /fotos/{agendamentoId}/fotos/{fotoId}/imagem` → deleta arquivos antigos e faz novo upload com geração de watermark + thumbnail

## 5. Regras Específicas
1. **Três versões de cada foto**: `originalPath` (upload original), `watermarkedPath` (com marca d'água), `thumbPath` (thumbnail 300×200). Todos gerados no upload.
2. **Fallback de processamento**: Se watermark ou thumbnail falharem, o caminho original é usado como fallback (silencioso).
3. **Validação de status do agendamento**: Upload só é permitido se o agendamento estiver em um dos 5 status específicos. Caso contrário, lança `EnsaioNaoFinalizadoException`.
4. **`FotoEnsaio` tem `fotoEdicaoId`**: Vincula ao `FotoEdicao` do módulo `edicao`, usado quando a foto é criada via `revisarFoto()`.
5. **`destaque` boolean**: Campo para marcar foto como destaque (usado no e-commerce).
6. **Tags via `@ElementCollection`**: Lista de strings armazenadas em tabela separada.

## 6. Testes
Nenhum teste específico para este módulo.

## 7. Pontos de Atenção
- **`FotoService` tem lógica duplicada com `EdicaoService.publicarNoEcommerce()`**: Ambos geram watermark e thumbnail, com a mesma string de texto e opacidade. `publicarNoEcommerce` no módulo `edicao` replica a lógica de `ImageProcessingService`.
- **`deletarArquivo()` engole exceções**: Falhas ao deletar arquivos do disco são silenciosamente ignoradas.
- **`Tags` como `@ElementCollection`**: Sem cascade, sem orphanRemoval — tags órfãs podem persistir se a lista for substituída.
- **`findPublicadasVisiveisByAgendamentoId`**: Query usada pelo módulo `ecommerce` — idealmente estaria centralizada neste módulo.
