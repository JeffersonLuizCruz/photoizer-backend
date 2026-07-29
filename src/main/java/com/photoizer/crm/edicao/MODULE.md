# Módulo: Edição

## 1. Responsabilidade
Gerencia o processo de edição de fotos do ensaio. Controla o fluxo desde o upload das fotos RAW (fotógrafo) até a edição (editor), revisão, conclusão e publicação no e-commerce/loja. É ativado automaticamente quando o pagamento final do agendamento é registrado.

## 2. Estrutura
```
edicao/
├── model/
│   ├── Edicao.java              # Entidade JPA (extends BaseEntity): agendamentoId, status, fotografo, editor, datas, observacoes
│   ├── FotoEdicao.java          # Entidade JPA (extends BaseEntity): edicaoId, rawPath, editedPath, status, ordem, aprovado, comentario
│   ├── StatusEdicao.java        # Enum: AGUARDANDO_RAW, RAW_ENVIADOS, EM_EDICAO, EDICAO_CONCLUIDA
│   └── StatusFotoEdicao.java    # Enum: RAW, EDITADO
├── repository/
│   ├── EdicaoRepository.java    # JpaRepository + findByAgendamentoId, existsByAgendamentoId, findAllByOrderByUpdatedAtDesc, findByStatusOrderByUpdatedAtDesc
│   └── FotoEdicaoRepository.java # JpaRepository + findByEdicaoId, countByEdicaoIdAndStatus
├── service/
│   └── EdicaoService.java       # 534 linhas: upload raw, upload editadas, concluir, publicar, revisar, zips
├── api/
│   ├── EdicaoController.java    # 233 linhas: 14 endpoints REST
│   ├── EdicaoResponse.java      # Record: id, agendamentoId, status, fotografo, editor, datas, contagens
│   ├── FotoEdicaoResponse.java  # Record: id, ordem, rawFileName, editedFileName, status, aprovado, comentario
│   ├── RevisaoRequest.java      # Record: aprovado (Boolean), comentario
│   └── ZipJobResponse.java      # Record: jobId, status
├── event/
│   ├── RawEnviadosEvent.java    # Publicado ao enviar RAW: agendamentoId, quantidade
│   ├── EdicaoConcluidaEvent.java# Publicado ao concluir edição: agendamentoId
│   └── FotosPublicadasEvent.java# Publicado ao publicar no ecommerce/loja: agendamentoId, quantidade
├── listener/
│   └── EdicaoListener.java      # Cria Edicao automaticamente ao receber PagamentoFinalRegistradoEvent
└── exception/
    ├── EdicaoNaoEncontradaException.java
    ├── FotoEdicaoNaoEncontradaException.java
    ├── FotoSemRawException.java
    └── StatusEdicaoInvalidoException.java
```

## 3. Dependências Externas

### Módulos internos (importados diretamente)
| Módulo | Uso |
|--------|-----|
| **agenda** | `Agendamento`, `AgendamentoRepository`, `StatusAgendamento` (validar status antes de upload RAW) |
| **auth** | `User`, `UserRepository` (fotografo/editor + getCurrentUser) |
| **foto** | `FotoEnsaio`, `FotoEnsaioRepository`, `StatusFoto`, `ImageProcessingService` (watermark + thumbnail) |
| **shared** | `BaseEntity`, `FileStorageService` |

### Eventos consumidos
| Evento | Ação |
|--------|------|
| `PagamentoFinalRegistradoEvent` (agenda) | Cria `Edicao` com status `AGUARDANDO_RAW` se não existir |

### Eventos publicados
| Evento | Consumidores |
|--------|-------------|
| `RawEnviadosEvent` | — (reservado para futuras notificações) |
| `EdicaoConcluidaEvent` | — (reservado) |
| `FotosPublicadasEvent` | — (reservado) |

## 4. Fluxos Principais

### Fluxo 1: Criação Automática da Edição
1. `PagamentoFinalRegistradoEvent` é publicado pelo módulo `agenda`
2. `EdicaoListener.handlePagamentoFinal()`:
   - Verifica se já existe `Edicao` para o `agendamentoId`
   - Se não existir, cria com `status = AGUARDANDO_RAW`

### Fluxo 2: Upload RAW (Fotógrafo)
1. `POST /api/v1/edicao/{agendamentoId}/raw` (multipart) → `EdicaoService.uploadRaw()`:
   - Valida: agendamento deve estar `EM_EDICAO` ou `AGUARDANDO_PAGAMENTO_FINAL`
   - Cria `Edicao` se não existir (lazy init)
   - Para cada arquivo: salva em `uploads/{agendamentoId}/raw/`, cria `FotoEdicao` com `status = RAW`
   - Atualiza `Edicao.status = RAW_ENVIADOS` e `dataEnvioRaw = now`
   - Se agendamento estava `AGUARDANDO_PAGAMENTO_FINAL`, muda para `EM_EDICAO`
   - Publica `RawEnviadosEvent`

### Fluxo 3: Upload Editadas (Editor)
1. `POST /api/v1/edicao/{agendamentoId}/editadas` (multipart) → `EdicaoService.uploadEditadas()`:
   - Match por nome de arquivo: busca `FotoEdicao.RAW` com `rawFileName` igual ao nome do arquivo enviado
   - Se não encontrar → `FotoSemRawException`
   - Salva em `uploads/{agendamentoId}/edit/`, atualiza `status = EDITADO`
   - Atualiza `Edicao.status = EM_EDICAO`

### Fluxo 4: Fluxo de Revisão
1. `PATCH /edicao/fotos/{fotoId}/revisao` → `revisarFoto()`:
   - Se `aprovado = true` e `editedPath != null`: gera watermark + thumbnail e salva como `FotoEnsaio` (status `INEDITA`)
   - Se `aprovado = false` e já existe `FotoEnsaio.INEDITA`: deleta a `FotoEnsaio`

### Fluxo 5: Conclusão e Publicação
1. `PATCH /edicao/{agendamentoId}/concluir` → `concluirEdicao()`:
   - Valida: pelo menos uma foto editada
   - Seta `status = EDICAO_CONCLUIDA`, `dataEnvioEditado = now`
   - Publica `EdicaoConcluidaEvent`
2. `PATCH /edicao/{agendamentoId}/publicar` → `publicarNoEcommerce()`:
   - Valida `status == EDICAO_CONCLUIDA`
   - Para cada foto editada: gera watermarked + thumbnail + cria `FotoEnsaio` com `status = PUBLICADA`
   - Atualiza agendamento para `SELECAO_DAS_FOTOS`
   - Publica `FotosPublicadasEvent`
3. `PATCH /edicao/{agendamentoId}/publicar-loja` → `publicarLoja()`:
   - Similar, mas busca `FotoEnsaio.INEDITA` e muda para `PUBLICADA`
   - Se não há inéditas, verifica se já existem publicadas e apenas atualiza status do agendamento

### Status Machine
```
PagamentoFinalRegistradoEvent
         │
         ▼
   AGUARDANDO_RAW ──uploadRaw──▶ RAW_ENVIADOS ──uploadEditadas──▶ EM_EDICAO ──concluir──▶ EDICAO_CONCLUIDA
         │                              │                                                    │
         └── (lazy) ──uploadRaw──┘     └── (lazy via uploadRaw)                              ├──publicar──▶ SELECAO_DAS_FOTOS (ecommerce)
                                                                                             └──publicar-loja──▶ SELECAO_DAS_FOTOS (loja)
```

## 5. Regras Específicas
1. **Match de arquivos por nome**: Fotos editadas são vinculadas às RAW pelo nome do arquivo (`rawFileName == originalFilename`). Se o nome for diferente, o upload é rejeitado (`FotoSemRawException`).
2. **Dois endpoints de publicação**: `publicarNoEcommerce` (cria `FotoEnsaio.PUBLICADA` com watermark/thumbnail) e `publicarLoja` (muda `FotoEnsaio.INEDITA` para `PUBLICADA`). Ambas avançam o agendamento para `SELECAO_DAS_FOTOS`.
3. **Geração de watermark + thumbnail**: Usa `ImageProcessingService` com texto `"© Photoizer Studio"` e opacidade 15%. Se falhar, usa o caminho original como fallback.
4. **Edição é criada lazy**: A `Edicao` é criada apenas no primeiro upload RAW se não existir (além da criação automática via listener).
5. **Revisão individual com efeito colateral**: Ao aprovar uma foto, uma `FotoEnsaio` é criada imediatamente (não apenas na publicação em lote). Ao rejeitar, a `FotoEnsaio.INEDITA` é deletada.
6. **`getCurrentUser()` lê do SecurityContextHolder**: Extrai o `principal` (userId como String), busca no `UserRepository` para associar o fotógrafo à edição.

## 6. Testes
Nenhum teste específico para este módulo.

## 7. Pontos de Atenção
- **`uploadRaw()` aceita `AGUARDANDO_PAGAMENTO_FINAL`**: O upload RAW pode ocorrer antes do pagamento final (quando o fotógrafo já tem as fotos). Nesse caso, o status do agendamento é atualizado para `EM_EDICAO`. Isso significa que o upload RAW pode avançar o status do agendamento — efeito colateral entre módulos.
- **`publicarNoEcommerce` vs `publicarLoja`**: Duas formas de publicar, com lógicas similares mas não idênticas. `publicarNoEcommerce` gera watermark/thumbnail e cria `FotoEnsaio` do zero. `publicarLoja` busca `FotoEnsaio.INEDITA` existente (criada via `revisarFoto`). Isso gera confusão sobre qual endpoint usar quando.
- **`reordenarFotos()` recebe `List<Map<String, Object>>`**: Sem tipo seguro, sem validação de schema. O parsing de `id` e `ordem` é manual.
- **`downloadZip` (ecommerce) vs `gerarZipRaw`/`gerarZipEditadas` (edicao)**: Lógica de geração de ZIP duplicada entre os dois módulos.
- **ZIPs temporários não limpos**: `gerarZipRaw` e `gerarZipEditadas` criam arquivos em `uploads/temp/` com limpeza apenas de arquivos com o mesmo prefixo — ZIPs antigos de outras execuções podem acumular.
- **`getCurrentUser()` quebra com JWT anônimo**: Se `auth.getName()` não for um UUID válido, lança exceção. Também faz query ao banco em todo `uploadRaw()`.
