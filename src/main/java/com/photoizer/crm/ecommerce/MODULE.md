# Módulo: E-commerce

## 1. Responsabilidade
Gerencia a galeria pública do cliente (via token), seleção de fotos para o pacote, carrinho + checkout de fotos extras (`CompraExtra`), comprovantes, download (individual/ZIP), favoritos (wishlist), comentários por foto e avaliações (depoimentos).

## 2. Estrutura
```
ecommerce/
├── model/
│   ├── CompraExtra.java       # Entidade: agendamentoId, valorTotal, quantidadeFotos, metodoPagamento, status, urlComprovante, dataPagamento, observacao, motivoRecusa
│   ├── StatusCompraExtra.java # Enum (State Pattern): AGUARDANDO_COMPROVANTE, AGUARDANDO_CONFIRMACAO, PAGA, CANCELADA — com métodos de transição
│   ├── MetodoPagamento.java   # Enum: PIX, TRANSFERENCIA, DINHEIRO
│   ├── StatusSessao.java      # Enum: ATIVA, FINALIZADA, CANCELADA
│   ├── ItemCarrinho.java      # Entidade: agendamentoId, fotoId, sessionId
│   ├── Favorito.java          # Entidade: agendamentoId, fotoId, sessionId (wishlist)
│   ├── FotoComentario.java    # Entidade: fotoId, agendamentoId, autorNome, mensagem, origem, lida
│   ├── OrigemComentario.java  # Enum: CLIENTE, STAFF
│   ├── Avaliacao.java         # Entidade: clienteId, agendamentoId, pacoteId, pontuacao(1-5), comentario, depoimento, aprovado
│   └── Sessao.java            # Entidade legada: status agora usa enum StatusSessao
├── repository/
│   ├── CompraExtraRepository.java
│   ├── ItemCarrinhoRepository.java
│   ├── FavoritoRepository.java
│   ├── FotoComentarioRepository.java
│   ├── AvaliacaoRepository.java
│   └── SessaoRepository.java
├── service/
│   ├── EcommerceService.java         # Orquestrador fino (~200 linhas): checkout, upload comprovante, regen token. Delega seleção/carrinho/queries.
│   ├── GaleriaQueryService.java      # Fachada read-only: buscarAgendamentoPorToken, valorUnitarioFotoExtra, listarFotosPublicadas, isDownloadPermitido
│   ├── CarrinhoService.java          # Facade: adicionar/remover/listar/contar/calcular carrinho
│   ├── FavoritoService.java          # Facade: adicionar/remover/listar favoritos (wishlist)
│   ├── DownloadService.java          # Facade: download foto individual + ZIP com limpeza de temp
│   ├── PagamentoExtraService.java    # State Pattern: confirmarPagamento, simularPagamento, cancelarCompra — transições via enum StatusCompraExtra
│   ├── SelecaoFotosService.java      # Facade: seleção/desseleção de fotos com validação de limite e bloqueio de remoção
│   ├── CompraQueryService.java       # Query Service Facade: queries admin de compra (detalhe, paginado, relatório, comprovante)
│   ├── ComentarioService.java        # Comentários de clientes + resposta staff + marcar lidos
│   ├── AvaliacaoService.java         # Service Layer: CRUD de avaliações/depoimentos
│   └── SessionService.java           # Sessão assinada HMAC-SHA256 (UUID v4 + assinatura)
├── api/ (7 controllers + ~25 DTOs/records)
│   ├── EcommerceMapper.java          # MapStruct Mapper: CompraExtra/FotoComentario/Avaliacao/Sessao → Response
│   ├── EcommerceController.java
│   ├── AdminComprasController.java
│   ├── AdminEcommerceController.java
│   ├── AdminAnalyticsController.java
│   ├── AdminComentariosController.java
│   ├── AvaliacaoController.java
│   └── SessaoController.java         # Legado
├── event/
│   ├── CompraExtraCriadaEvent.java
│   ├── CompraExtraConfirmadaEvent.java
│   ├── CompraExtraFotosAssociadasEvent.java  # NOVO: associa fotos à compra no checkout
│   ├── CompraExtraCanceladaEvent.java         # NOVO: desassocia fotos no cancelamento
│   ├── CompraExtraPagaEvent.java              # NOVO: marca fotos como PAGA
│   ├── FotosSelecionadasEvent.java            # NOVO: seleção/desseleção de fotos
│   ├── FotoDownloadEvent.java                 # NOVO: registro de download
│   └── TokenGaleriaRegeneradoEvent.java       # NOVO: regeneração de token
├── listener/
│   └── (vazio — listeners em módulos foto/agenda: FotoEcommerceEventListener, AgendamentoEcommerceEventListener)
└── exception/
    ├── TokenExpiradoException.java
    ├── GaleriaNaoEncontradaException.java   # 404
    ├── CompraNaoEncontradaException.java    # 404
    ├── FotoNaoEncontradaException.java      # 404
    ├── CarrinhoVazioException.java          # 422
    ├── FotoJaSelecionadaException.java      # 409
    ├── FotoJaBaixadaException.java          # 409
    ├── LimitePacoteExcedidoException.java   # 422
    ├── CompraJaPagaException.java           # 409
    ├── SessaoInvalidaException.java         # 401
    └── FotoIndisponivelException.java       # 422
```

## 3. Design Patterns Aplicados

| Pattern | Onde | Motivo |
|---------|------|--------|
| **Facade** | `CarrinhoService`, `FavoritoService`, `DownloadService`, `GaleriaQueryService` | Isolar responsabilidades do God class EcommerceService em beans coesos |
| **State** | `StatusCompraExtra` enum | Transições de estado válidas centralizadas no enum, eliminando if/else espalhados |
| **Facade** | `PagamentoExtraService` | Orquestra transições de estado de CompraExtra usando State Pattern |
| **Mapper (MapStruct)** | `EcommerceMapper` | Elimina `static of()` manuais nos DTOs; consistência com agenda/despesa/edicao |
| **Facade** | `SelecaoFotosService` | Isola validação de limite de pacote e bloqueio de remoção de fotos baixadas |
| **Query Service Facade** | `CompraQueryService` | Separa queries admin do orquestrador (CQRS leve); reduz god class |
| **Service Layer** | `AvaliacaoService` | Controller não deve injetar repository diretamente |
| **Dependency Injection** | `EcommerceService.checkout()` | CarrinhoService injetado via construtor (antes recebido como parâmetro) |

## 4. Dependências Externas

### Módulos internos importados
| Módulo | Uso | Tipo |
|--------|-----|------|
| **agenda** | `Agendamento` (via `GaleriaQueryService`) — token galeria | leitura |
| **foto** | `FotoEnsaio` (via `GaleriaQueryService` e `FotoEnsaioRepository` para queries read-only) — fotos da galeria | leitura |
| **config** | `ConfiguracaoService` (`valorUnitarioFotoExtra` default R$ 15,00) | leitura |
| **financeiro** | consome eventos `CompraExtraCriadaEvent`/`CompraExtraConfirmadaEvent` | eventos |
| **shared** | `AuditInfo`, `FileStorageService` | infraestrutura |

> **MODULITH**: O módulo ecommerce NÃO escreve diretamente em entidades de outros módulos.
> Escritas são feitas via eventos (`CompraExtraFotosAssociadasEvent`, `CompraExtraCanceladaEvent`, `CompraExtraPagaEvent`, `FotoDownloadEvent`, `FotosSelecionadasEvent`, `TokenGaleriaRegeneradoEvent`).
> Listeners nos módulos foto (`FotoEcommerceEventListener`) e agenda (`AgendamentoEcommerceEventListener`) processam os eventos.

### Eventos publicados
| Evento | Consumidores |
|--------|-------------|
| `CompraExtraCriadaEvent` | `financeiro.FinanceiroEventListener` |
| `CompraExtraConfirmadaEvent` | `financeiro.FinanceiroEventListener` |
| `CompraExtraFotosAssociadasEvent` | `foto.FotoEcommerceEventListener` |
| `CompraExtraCanceladaEvent` | `foto.FotoEcommerceEventListener` |
| `CompraExtraPagaEvent` | `foto.FotoEcommerceEventListener` |
| `FotosSelecionadasEvent` | `foto.FotoEcommerceEventListener` |
| `FotoDownloadEvent` | `foto.FotoEcommerceEventListener` |
| `TokenGaleriaRegeneradoEvent` | `agenda.AgendamentoEcommerceEventListener` |

## 5. Melhorias Aplicadas (Refactor)

| # | Melhoria | Impacto |
|---|----------|---------|
| 1 | **Split God Class**: `EcommerceService` (574→~200 linhas) extraindo `CarrinhoService`, `FavoritoService`, `DownloadService`, `PagamentoExtraService`, `GaleriaQueryService`, `SelecaoFotosService`, `CompraQueryService` | Manutenibilidade |
| 2 | **Query `findByCompraExtraId`**: substitui 4x `findAll().stream().filter()` por query dedicada no banco | Performance (P1) |
| 3 | **10 exceções de domínio**: substituem `RuntimeException`/`IllegalArgumentException` genéricas | UX, tratamento de erros |
| 4 | **State Pattern em `StatusCompraExtra`**: transições de estado centralizadas no enum | Manutenibilidade |
| 5 | **`Sessao.status` como enum** (`StatusSessao`): elimina `String` crua | Type safety |
| 6 | **Remoção de `PERCENTUAL_COMISSAO_PADRAO`** (código morto) | Limpeza |
| 7 | **`CarrinhoItemResponse.subtotal`** corrigido (multiplicava por 1) | Bug fix |
| 8 | **`ComentarioService`** delega para `GaleriaQueryService` (elimina duplicação) | DRY |
| 9 | **`AdminEcommerceController`** não injeta mais `AgendamentoRepository` diretamente | Modulith |
| 10 | **`DownloadService.downloadZip`** limpa arquivos temporários (corrige resource leak) | Resource management |
| 11 | **Desacoplamento cross-module**: 6 eventos criados + 2 listeners; `EcommerceService` não escreve mais em `FotoEnsaio`/`Agendamento` | Modulith |
| 12 | **`BaseEntity` → `AuditInfo`**: composição em vez de herança; entidade removida | Clean Architecture |
| 13 | **`EcommerceMapper` (MapStruct)**: elimina `static of()` manuais em 4 DTOs (CompraExtra, FotoComentario, Avaliacao, Sessao) | Type-safe mapping, consistência |
| 14 | **`CompraQueryService` extraído**: 10 métodos de query movidos do EcommerceService (CQRS leve) | SRP, god class reduzida |
| 15 | **`SelecaoFotosService` extraído**: lógica de seleção de fotos isolada com validação de limite | SRP, testabilidade |
| 16 | **`AvaliacaoService` criado**: controller não injeta mais repository diretamente | Service Layer |
| 17 | **`cancelarCompra` duplicado removido**: lógica idêntica em EcommerceService e PagamentoExtraService → consolidada em PagamentoExtraService | DRY |
| 18 | **`EcommerceService.checkout()` injetado via construtor**: elimina CarrinhoService como parâmetro de método | Dependency Injection |
| 19 | **`AdminAnalyticsController` refatorado**: usa `EcommerceQueryService` em vez de injetar repositorios | Service Layer |
| 20 | **Testes unitários atualizados**: `EcommerceServiceTest` refleta nova estrutura de dependências | Manutenibilidade |

## 6. Pendências Restantes

| # | Pendência | Prioridade |
|---|-----------|------------|
| 1 | ~~**`AdminAnalyticsController`** ainda injeta repositórios diretamente no controller~~ | **RESOLVIDO** |
| 2 | ~~**DTOs manuais** (`static of()`, `.name()`): migrar para MapStruct~~ | **RESOLVIDO** (CompraExtra, FotoComentario, Avaliacao, Sessao) |
| 3 | **`Sessao`/`SessaoController` legado**: remover ou integrar | P3 |
| 4 | ~~**Testes unitários/integração**: nenhum teste específico existe~~ | **PARCIAL** (EcommerceServiceTest, SessionServiceTest, EcommerceSecurityTests atualizados) |
| 5 | **Optimistic locking** (`@Version`): ausente em todas as entidades | P2 |
| 6 | **`SessaoResponse.of()`**: único DTO com `static of()` restante (legado) | P3 |