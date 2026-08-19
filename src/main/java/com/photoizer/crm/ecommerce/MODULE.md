# Módulo: E-commerce

## 1. Responsabilidade
Gerencia a galeria pública do cliente (via token), seleção de fotos para o pacote, carrinho + checkout de fotos extras (`CompraExtra`), comprovantes, download (individual/ZIP), favoritos (wishlist), comentários por foto e avaliações (depoimentos). É o maior módulo do sistema (`~3.4k LOC`, 7 entidades, 6 services, 8 controllers).

> **Nota**: O MODULE.md anterior descrevia `Pedido`/`Cupom`/`PedidoService`/`AdminPedidoController` — **essas classes não existem mais no código atual** (foram removidas; somente `CompraExtra` mantém o fluxo de venda). Este documento reflete a implementação atual.

## 2. Estrutura
```
ecommerce/
├── model/
│   ├── CompraExtra.java       # Entidade (extends BaseEntity): agendamentoId, valorTotal, quantidadeFotos, metodoPagamento, status, urlComprovante, dataPagamento, observacao, motivoRecusa
│   ├── StatusCompraExtra.java # Enum: AGUARDANDO_COMPROVANTE, AGUARDANDO_CONFIRMACAO, PAGA, CANCELADA
│   ├── MetodoPagamento.java   # Enum: PIX, CREDITO, DEBITO, BOLETO, DINHEIRO
│   ├── ItemCarrinho.java      # Entidade: agendamentoId, fotoId, sessionId
│   ├── Favorito.java          # Entidade: agendamentoId, fotoId, sessionId (wishlist)
│   ├── FotoComentario.java    # Entidade: fotoId, agendamentoId, autorNome, mensagem, origem, lida
│   ├── OrigemComentario.java  # Enum: CLIENTE, STAFF
│   ├── Avaliacao.java         # Entidade: clienteId, agendamentoId, pacoteId, pontuacao(1-5), comentario, depoimento, aprovado
│   └── Sessao.java            # Entidade legada: clienteId, nomeSessao, dataRealizacao, local, descricao, status String — **não integrada ao fluxo principal**
├── repository/
│   ├── CompraExtraRepository.java     # JpaRepository + queries por status/período, totalPorStatus (SUM)
│   ├── ItemCarrinhoRepository.java    # JpaRepository + delete por session+agenda
│   ├── FavoritoRepository.java        # JpaRepository + findBySessionIdAndFotoId
│   ├── FotoComentarioRepository.java  # JpaRepository + findByFotoId/AgendamentoId
│   ├── AvaliacaoRepository.java       # JpaRepository + findByAprovadoTrue/ByClienteId
│   └── SessaoRepository.java          # JpaRepository (sem queries customizadas)
├── service/
│   ├── EcommerceService.java    # 574 linhas: galeria, seleção, carrinho, checkout, comprovante, favoritos, download/ZIP, admin
│   ├── SessionService.java      # 75 linhas: emite/valida sessão de carrinho assinada HMAC-SHA256 (UUID v4 + assinatura)
│   └── ComentarioService.java   # 144 linhas: comentários de clientes + resposta staff + marcar lidos
├── api/ (8 controllers + ~30 DTOs/records)
│   ├── EcommerceController.java         # Galeria pública, carrinho, checkout, comprovante, favoritos, download, comentários cliente
│   ├── AdminComprasController.java      # Admin: listar paginado/filtrável, detalhe, confirmar/cancelar, relatório
│   ├── AdminEcommerceController.java    # Admin por agendamento: resumo, override seleção, regen token
│   ├── AdminAnalyticsController.java    # Admin: métricas (receita, conversão, populares)
│   ├── AdminComentariosController.java  # Admin: listar por agendamento, responder, marcar lidos
│   ├── AvaliacaoController.java         # CRUD de avaliações/depoimentos
│   └── SessaoController.java            # CRUD de Sessao (legado)
├── event/
│   ├── CompraExtraCriadaEvent.java      # Publicado no checkout
│   └── CompraExtraConfirmadaEvent.java  # Publicado ao marcar compra como PAGA
├── listener/
│   └── (vazio — eventos consumidos em financeiro: FinanceiroEventListener)
└── exception/
    └── TokenExpiradoException.java      # Token da galeria expirado
```

## 3. Dependências Externas

### Módulos internos importados — **[VIOLAÇÕES Modulith]**
| Módulo | Uso | Tipo |
|--------|-----|------|
| **agenda** | `Agendamento`, `AgendamentoRepository` — token galeria, valorExtras, status; `EcommerceService` **muta `Agendamento`** (regenerar token, setar valorExtras) | entrada **e escrita** |
| **foto** | `FotoEnsaio`, `FotoEnsaioRepository`, `StatusFoto`, `FotoEnsaioResponse`, `FotoService` — fotos da galeria; `EcommerceService`/`AdminAnalyticsController` **muta `FotoEnsaio`** (status PAGA, compraExtraId, selecionadaPacote, dataDownload) | entrada **e escrita** |
| **config** | `ConfiguracaoService` (`valorUnitarioFotoExtra` default R$ 15,00) | leitura |
| **financeiro** | consome eventos `CompraExtraCriadaEvent`/`CompraExtraConfirmadaEvent` (via `FinanceiroEventListener`) | eventos |
| **shared** | `BaseEntity`, `FileStorageService` (comprovante) | infraestrutura |

> BOA PRÁTICA: **financeiro** consome os eventos do ecommerce — este é o padrão Modulith correto. As violações estão no próprio `EcommerceService` (escreve em `FotoEnsaio` e `Agendamento`).

### Eventos publicados
| Evento | Consumidores |
|--------|-------------|
| `CompraExtraCriadaEvent` | `financeiro.FinanceiroEventListener` |
| `CompraExtraConfirmadaEvent` | `financeiro.FinanceiroEventListener` |

### Eventos consumidos
Nenhum.

## 4. Fluxos Principais

### Fluxo 1: Galeria Pública (Cliente)
1. `POST /ecommerce/sessao` → `SessionService.emitir()`: gera `UUIDv4.assinatura` HMAC-SHA256.
2. `GET /ecommerce/galeria/{token}` → fotos publicadas/visíveis (`FotoEnsaioResponse.ofPublic`).
3. `PATCH /galeria/{token}/selecionar` → `selecionarFotos()`: valida limite do pacote; bloqueia remoção de foto já baixada.
4. Carrinho: adicionar/remover/listar/contar (`X-Session-Id` validada pelo `SessionService`).

### Fluxo 2: Checkout e Pagamento de Extras
1. `POST /galeria/{token}/checkout` → `checkout()` (`EcommerceService.java:200-245`): valida carrinho, cria `CompraExtra` `AGUARDANDO_COMPROVANTE`, associa `compraExtraId` nas fotos, limpa carrinho, publica `CompraExtraCriadaEvent`.
2. `POST /galeria/{token}/comprovante` → `uploadComprovante()`: salva comprovante, status → `AGUARDANDO_CONFIRMACAO`.
3. `PATCH /admin/compras/{id}/confirmar` → `confirmarPagamento()` → `marcarCompraPaga()` (`:329-344`): status → `PAGA`, marca fotos `PAGA`, publica `CompraExtraConfirmadaEvent`.
4. `simularPagamento()` (`:314-327`): endpoint dev — libera sem comprovante.
5. `cancelarCompra()` (`:483-507`): status → `CANCELADA`, desvinca fotos.

### Fluxo 3: Download
- `GET /galeria/{token}/download/{fotoId}` → `downloadFoto()` (`:361-377`): libera se `selecionadaPacote || PAGA`; seta `dataDownload`.
- `GET /galeria/{token}/download-zip` → `downloadZip()` (`:543-573`): ZIP em temp dir; atualiza `dataDownload` de todas.

### Fluxo 4: Comentários e Avaliações
- Cliente comenta em foto (`ComentarioService.comentarCliente`, origem `CLIENTE`); staff responde (`responderStaff`, origem `STAFF`, lida=true); admin lista por agendamento com contagem de não-lidos.
- `AvaliacaoController` — CRUD simples de depoimentos (sem validação de pertencimento a agendamento).

## 5. Regras Específicas
1. **Sessão assinada HMAC-SHA256** (`SessionService`): mesmo padrão do JWT (`app.jwt.secret`); sessão forjada é rejeitada pelo header `X-Session-Id`.
2. **Token galeria com expiração** (15 dias) gerenciado pela agenda; `TokenExpiradoException` se expirado.
3. **Preço extra** vem do pacote (`precoFotoExtra > 0`) ou da config `valorUnitarioFotoExtra` (default R$ 15,00).
4. **Pertecença protegida por checks manuais**: cada método chama `buscarAgendamentoPorToken` e valida `agendamentoId` do item/foto — bastante repetido, com exceções genéricas.
5. **`Sessao`/`SessaoController` legado**: entidade não integrada ao fluxo da galeria (que usa `SessionService`).

## 6. Testes
Nenhum teste específico para este módulo. Apenas `CrmApplicationTests` (smoke de contexto).

## 7. Dívidas Técnicas e Melhorias Recomendadas

### 7.1 `EcommerceService` é god class (574 linhas, +20 métodos) — **P1**
- Mistura galeria, carrinho, checkout, pagamento, favoritos, download, ZIP, métricas e admin em um único bean.
- **Solução**: splits — `CarrinhoService`, `PagamentoExtraService`, `DownloadService`, `GaleriaQuery`, `AdminEcommerceQuery`; manter `EcommerceService` como orquestrador fino.

### 7.2 Escrita cross-module em `FotoEnsaio` e `Agendamento` — **[CRÍTICO] P1**
- `marcarCompraPaga` (`:329-344`) e `cancelarCompra` (`:483-507`) **mutam `FotoEnsaio.status`/`compraExtraId`** (módulo foto); `regerarToken`/`setValorExtras` **mutam `Agendamento`** (módulo agenda).
- **Solução**: evento `CompraExtraConfirmadaEvent` já publicado — o módulo **foto** deve ter listener que marca `FotoEnsaio.PAGA`; regeneração de token via evento no módulo agenda. Os repros de foto/agenda não deveriam ser injetados no `EcommerceService`.

### 7.3 `findAll()` para achar fotos por `compraExtraId` — **[CRÍTICO] P1**
- `marcarCompraPaga` (`:334-336`), `cancelarCompra` (`:498-500`), `buscarCompraDetalhe*` (`:403-404, 467-468`) fazem `fotoEnsaioRepository.findAll().stream().filter(...)` — **carregam o banco inteiro de fotos em memória**.
- **Solução**: query dedicada `findByCompraExtraId(UUID)` no `FotoEnsaioRepository` (módulo foto).

### 7.4 Exceções genéricas `RuntimeException`/`IllegalArgumentException` — **P1**
- Dezenas de `orElseThrow(() -> new RuntimeException(...))` e `IllegalArgumentException` para regras de negócio (`:107, 158, 250, 292, 308, 364, 489...`).
- **Solução**: hierarquia central `BusinessException` + subtipos (`NotFoundException`, `IllegalStateException`, `ConflictException`).

### 7.5 Violações no controller (regra no controller) — **P2**
- `EcommerceController` injeta `FotoService` e `SessionService` e monta o `CarrinhoResponse` chamando `fotoService.buscarPorId` por item (`EcommerceController.java:119-127`) — lógica de negócio (query N+1) no controller.
- `AdminAnalyticsController` injeta `CompraExtraRepository`/`FotoEnsaioRepository` direto no controller (`AdminAnalyticsController.java:22-28`) com `fotoEnsaioRepository.findAll()`.

### 7.6 Regras de pagamento duplicadas — **P2**
- Fluxo `confirmarPagamento`/`simularPagamento`/`marcarCompraPaga` repetem validações e mutações; `AdminComprasController.confirmar` e `AdminEcommerceController` têm lógica similar.
- **Solução**: `PagamentoExtraService` único com estados transicionáveis (usar padrão State via enum no `CompraExtra`).

### 7.7 `Sessao` e `SessaoController` legado — **P3**
- Entidade `Sessao` + controller (`/api/v1/sessoes`) não participam do fluxo de galeria (que usa `SessionService`). Código morto/latente.
- **Solução**: remover ou integrar; registrar intenção no backlog.

### 7.8 DTOs manuais e `.name()` de enums — **P2**
- `CompraExtraResponse.ofPublic/ofAdmin`, `AdminCompraDetalheResponse`, `CarrinhoResponse` etc. montados à mão; `getStatus().name()`/`getMetodoPagamento().name()`.
- **Solução**: MapStruct com `@Mapping(toStatusName)`, `@Context` para URLs relativas; enums serializados como enum.

### 7.9 Herança `BaseEntity` → composição — **P1** (padrão-aplicável)
- Todas as 7 entidades estendem `@MappedSuperclass`.
- **Solução**: `@Embeddable AuditInfo` + Auditing; eliminar `BaseEntity`/`@SuperBuilder`.

### 7.10 Validações de pertencimento repetidas — **P3**
- `buscarAgendamentoPorToken` + checks `agendamentoId` duplicados em quase todos os métodos.
- **Solução**: `GaleriaAppService` com `@Transactional` por caso de uso já faz parte do contexto; delegar o check a um método privado único/ACL.

## 8. Exemplos de arquivos afetados
- `EcommerceService.java:200-245` — checkout (muta fotos/querries); `:329-344` — marca fotos `PAGA` via `findAll()`; `:483-507` — cancelar com `findAll()`; `:533-541` — regen token (muta agenda); `:543-573` — zip.
- `EcommerceController.java:119-127` — N+1 no controller via `FotoService`; `:63-68` — validar sessão; `AdminAnalyticsController.java:34-58` — `findAll()` fotos no controller.
- `ComentarioService.java:45-52` — duplica `buscarAgendamentoPorToken` do EcommerceService.
- `model/Sessao.java` + `api/SessaoController.java` — legado não integrado.