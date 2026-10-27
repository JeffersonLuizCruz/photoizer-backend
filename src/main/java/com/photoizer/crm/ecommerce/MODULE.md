# Módulo: E-commerce

## 1. Responsabilidade
Gerencia a galeria pública do cliente (via token), seleção de fotos para o pacote, carrinho + checkout de fotos extras (`CompraExtra`), comprovantes, download (individual/ZIP), favoritos (wishlist), comentários por foto e avaliações (depoimentos).

## 2. Estrutura
```
ecommerce/
├── model/
│   ├── CompraExtra.java       # Entidade: agendamentoId, valorTotal, quantidadeFotos, metodoPagamento, status, urlComprovante, linkPagamentoCartao, dataPagamento, observacao, motivoRecusa
│   ├── StatusCompraExtra.java # Enum (State Pattern): AGUARDANDO_COMPROVANTE, AGUARDANDO_CONFIRMACAO, PAGA, CANCELADA — com métodos de transição
│   ├── MetodoPagamento.java   # Enum: PIX, TRANSFERENCIA, DINHEIRO, CARTAO
│   ├── ConfiguracaoPagamentoEcommerce.java # Config global de PIX do e-commerce (linha única)
│   ├── StatusSessao.java      # Enum: ATIVA, FINALIZADA, CANCELADA
│   ├── ItemCarrinho.java      # Entidade: agendamentoId, fotoId, sessionId
│   ├── Favorito.java          # Entidade: agendamentoId, fotoId, sessionId (wishlist)
│   ├── FotoComentario.java    # Entidade: fotoId, agendamentoId, autorNome, mensagem, origem, lida
│   ├── OrigemComentario.java  # Enum: CLIENTE, STAFF
│   ├── Avaliacao.java         # Entidade: clienteId, agendamentoId, pacoteId, pontuacao(1-5), comentario, depoimento, aprovado
│   └── Sessao.java            # Entidade legada: status agora usa enum StatusSessao
├── repository/
│   ├── CompraExtraRepository.java
│   ├── ConfiguracaoPagamentoEcommerceRepository.java
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
│   ├── PagamentoExtraService.java    # State Pattern: confirmarPagamento, simularPagamento, cancelarCompra, atualizarLinkPagamentoCartao
│   ├── ConfiguracaoPagamentoEcommerceService.java # PIX global do e-commerce (altera e reflete em todas as galerias)
│   ├── SelecaoFotosService.java      # Facade: seleção/desseleção de fotos com validação de limite e bloqueio de remoção
│   ├── CompraQueryService.java       # Query Service Facade: queries admin de compra (detalhe, paginado, relatório, comprovante)
│   ├── ComentarioService.java        # Comentários de clientes + resposta staff + marcar lidos
│   ├── AvaliacaoService.java         # Service Layer: CRUD de avaliações/depoimentos
│   └── SessionService.java           # Sessão assinada HMAC-SHA256 (UUID v4 + assinatura)
├── api/ (7 controllers + ~25 DTOs/records)
│   ├── EcommerceMapper.java          # MapStruct Mapper: CompraExtra/FotoComentario/Avaliacao/Sessao → Response
│   ├── EcommerceController.java
│   ├── AdminComprasController.java
│   ├── AdminPagamentoEcommerceController.java
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
| `CompraExtraCriadaEvent` | `financeiro.FinanceiroEventListener` (soma extras no agendamento) |
| `CompraExtraConfirmadaEvent` | `financeiro.FinanceiroEventListener` (soma recebimento no agendamento) |
| `CompraExtraFotosAssociadasEvent` | `foto.FotoEcommerceEventListener` |
| `CompraExtraCanceladaEvent` | `foto.FotoEcommerceEventListener` + `financeiro.FinanceiroEventListener` (estorna extras) |
| `CompraExtraPagaEvent` | `foto.FotoEcommerceEventListener` |
| `FotosSelecionadasEvent` | `foto.FotoEcommerceEventListener` |
| `FotoDownloadEvent` | `foto.FotoEcommerceEventListener` |
| `TokenGaleriaRegeneradoEvent` | `agenda.AgendamentoEcommerceEventListener` |

### Pagamento de fotos extras
- `GET /api/v1/ecommerce/pagamento` (público) retorna o PIX atual do e-commerce.
- `GET/PUT /api/v1/admin/ecommerce/pagamento` (**ADMIN**) edita o PIX global; a alteração reflete em todas as galerias. O controle também fica disponível no workspace da galeria (aba Visão geral), por meio do `GaleriaPagamentoPixCard`.
- A chave não é copiada para a compra: o checkout e a seção `Minhas Compras` sempre exibem a configuração atual. Assim, uma correção feita pelo staff aparece nas compras PIX pendentes após a atualização/polling.
- `PATCH /api/v1/admin/ecommerce/compras/{id}/link-pagamento` (ADMIN/FOTOGRAFO) define/remove o link de cartão de uma compra.
- O cliente vê o PIX e o link de cartão no checkout; o link também aparece em `Minhas Compras` com atualização periódica.

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
| 21 | **Comprovantes servidos via `FileServeHelper`**: `EcommerceController` e `AdminComprasController` deixam de usar `new FileSystemResource(path)` cru e passam pelo helper, que confina o caminho ao diretório de uploads (defesa contra path traversal) | Segurança |

## 6. Pendências Restantes

| # | Pendência | Prioridade |
|---|-----------|------------|
| 1 | ~~**`AdminAnalyticsController`** ainda injeta repositórios diretamente no controller~~ | **RESOLVIDO** |
| 2 | ~~**DTOs manuais** (`static of()`, `.name()`): migrar para MapStruct~~ | **RESOLVIDO** (CompraExtra, FotoComentario, Avaliacao, Sessao) |
| 3 | **`Sessao`/`SessaoController` legado**: remover ou integrar | P3 |
| 4 | ~~**Testes unitários/integração**: nenhum teste específico existe~~ | **PARCIAL** (EcommerceServiceTest, SessionServiceTest, EcommerceSecurityTests atualizados) |
| 5 | **Optimistic locking** (`@Version`): ausente em todas as entidades | P2 |
| 6 | **`SessaoResponse.of()`**: único DTO com `static of()` restante (legado) | P3 |

## 7. Galeria com seleção e edição (V8/V9/V13/V15)

O ecommerce passou a suportar três modos por galeria:

- `DIRETO`: fluxo legado **desativado**. Permanece apenas como leitura para linhas antigas que não puderam ser migradas.
- `COM_EDICAO`: cliente envia a seleção, o staff baixa os originais, edita, sobe as versões editadas e publica antes da comercialização.
- `SEM_EDICAO`: cliente envia a seleção, o staff apenas a **aprova** e o pacote é entregue em versão original. Extras continuam sendo liberados somente após a confirmação do pagamento e não entram na fila de edição sob demanda.

### Modelo

- `GaleriaEnsaio` (`galerias_ensaio`): modo e fase (`SELECAO`, `SELECAO_ENVIADA`, `EM_EDICAO`, `COMERCIALIZACAO`). `SEM_EDICAO` pula `EM_EDICAO` e vai de `SELECAO_ENVIADA` para `COMERCIALIZACAO`.
- `SelecaoEnsaio` (`selecoes_ensaio`): snapshot imutável da seleção, com número e status. Em `SEM_EDICAO` a aprovação leva `ENVIADA` direto para `PUBLICADA`, sem marcar itens como editados.
- `SelecaoEnsaioItem` (`selecao_ensaio_itens`): foto, número de galeria, nome do arquivo e tipo (`PACOTE`/`EXTRA`).
- `FotoEnsaio`: ganhou `numeroGaleria`, `versaoAtual` (`ORIGINAL`/`EDITADA`), paths editados e `emSelecaoEdicao`.

### Endpoints

Públicos:
- `POST /api/v1/ecommerce/galeria/{token}/selecao`
- `GET /api/v1/ecommerce/galeria/{token}/selecao`
- `DELETE /api/v1/ecommerce/galeria/{token}/rascunho` (limpa só as fotos do pacote; extras do carrinho permanecem)

Staff:
- `GET /api/v1/admin/selecoes/pendentes` (inclui `requerEdicao`)
- `GET /api/v1/admin/agendamentos/{id}/selecoes`
- `GET /api/v1/admin/agendamentos/{id}/selecoes/{selecaoId}`
- `GET .../download-zip` (originais + `manifest.csv`; somente `COM_EDICAO`)
- `POST .../iniciar` (somente `COM_EDICAO`), `.../reabrir`, `.../publicar` (publica a edição ou aprova a seleção)
- `GET/PUT /api/v1/admin/agendamentos/{id}/galeria[/modo]`
- `PUT /api/v1/admin/agendamentos/{id}/galeria/titulo` (ADMIN/FOTOGRAFO/EDITOR)
- `GET /api/v1/admin/galerias/marca-dagua/fotos` e `POST .../fotos/{fotoId}/regerar` (ADMIN; manutenção global da marca d'água)
- `GET /api/v1/admin/ecommerce/extras/pendentes-edicao?agendamentoId=` (ADMIN/FOTOGRAFO/EDITOR)
- `PUT /api/v1/agendamentos/{id}/fotos/{fotoId}/editada-compra` (upload da edição sob demanda)
- `POST /api/v1/agendamentos/{id}/fotos/{fotoId}/editada-compra/publicar` (libera o download)

### Regras

1. O envio inclui fotos do pacote + extras do carrinho.
2. Depois do envio, os itens do pacote ficam bloqueados. O cliente ainda pode
   anexar/remover **extras** enquanto a seleção está ativa (`ENVIADA`,
   `EM_EDICAO` ou `CONCLUIDA`); o carrinho sincroniza o snapshot via
   `ItemCarrinhoAdicionadoEvent`/`ItemCarrinhoRemovidoEvent`.
3. O download/comercialização só é liberado após a publicação da edição
   (`COM_EDICAO`) ou após a aprovação da seleção (`SEM_EDICAO`).
4. Na comercialização a galeria mostra as fotos da seleção publicada
   (`versaoAtual = EDITADA` em `COM_EDICAO`, `ORIGINAL` em `SEM_EDICAO`) **e** as
   fotos restantes (fora da seleção). Em `COM_EDICAO` as restantes podem ser
   compradas e entram em edição sob demanda; em `SEM_EDICAO` são entregues em
   original após o pagamento.
5. O fluxo legado `DIRETO` foi desativado (`V13`). Galerias limpas são migradas
   para `COM_EDICAO/SELECAO`; `DIRETO` só permanece como leitura para linhas
   antigas com seleção/compra/download/edição. Novas galerias usam `COM_EDICAO`
   por padrão ou `SEM_EDICAO` quando configurado/selecionado.
6. O preço dos extras é congelado no snapshot da seleção no momento em que a
   foto entra no carrinho. `getValorUnitarioExtra` considera a seleção ativa
   antes da publicação e a publicada depois dela.
7. `EdicaoPublicadaEvent` troca a versão pública para `EDITADA`;
   `SelecaoAprovadaEvent` apenas libera o bloqueio de seleção e mantém
   `ORIGINAL`; `SelecaoCanceladaEvent` libera a seleção.
8. `SelecaoEnviadaEvent` notifica o editor/fotógrafo e carrega `requerEdicao`
   para a mensagem correta. Extras anexados depois disparam
   `SelecaoExtraAdicionadaEvent`/`SelecaoExtraRemovidaEvent` para
   marcar/desmarcar `emSelecaoEdicao`.
9. Toda seleção (cliente ou admin) valida foto publicada, visível, numerada e
   sem compra/download (`SelecaoFotoValidator`).
10. O modo da galeria não pode ser alterado com seleção ativa/publicada, venda,
    download ou versão editada já enviada (`podeAlterarModo`).
11. Em `SEM_EDICAO`, uma seleção `PUBLICADA` pode ser **reaberta** (volta para
    `SELECAO`) enquanto nenhuma foto tiver sido baixada ou vendida. Em
    `COM_EDICAO` a seleção publicada continua imutável. A resposta da seleção
    expõe `podeReabrir` para a UI decidir se mostra a ação.
12. Em `COM_EDICAO`, a publicação exige que **todos** os itens do snapshot
    (pacote + extras) tenham versão editada. Em `SEM_EDICAO`, a aprovação não
    exige itens editados.
13. Em `COM_EDICAO`, extras comprados depois da publicação não entram em uma
    nova seleção: ficam `PAGA` + `versaoAtual = ORIGINAL` e aparecem na fila
    `GET /api/v1/admin/ecommerce/extras/pendentes-edicao`. Em `SEM_EDICAO` os
    extras pagos são baixados em original e **não** entram nessa fila.
14. O estúdio envia a versão editada em
    `PUT /api/v1/agendamentos/{id}/fotos/{fotoId}/editada-compra` e libera o
    download em `POST .../editada-compra/publicar` (troca `versaoAtual` para
    `EDITADA`). Antes da liberação o download permanece bloqueado.
15. A capa manual fica em `galerias_ensaio.capa_foto_id` + `capa_versao`; a versão é congelada e não muda sozinha após edição/publicação.
12. `PUT /api/v1/admin/agendamentos/{id}/galeria/capa` define/remove a capa (ADMIN/FOTOGRAFO/EDITOR). `GET /api/v1/ecommerce/galeria/{token}/capa` serve o hero em até 2560px; `capaUrl` já sai com `?v={fotoId}-{versao}` e as respostas usam `no-store`.
13. O endpoint público `GET /api/v1/ecommerce/fotos/{fotoId}/watermarked` serve a **prévia protegida de 560px e JPEG ~0.35** (`preview_v3_*`). Fotos antigas são reduzidas sob demanda a partir do watermark existente, em cache versionado.
14. A marca d'água das novas prévias usa `© {nomeContratada}` (fallback `nomeFotografo` → `Estúdio`). Arquivos finais de download continuam sem marca e em alta qualidade.
15. A resposta pública da galeria inclui `valoresExtras` com o preço congelado por foto.
16. Ao enviar a seleção, fotos do pacote são removidas do carrinho para não serem cobradas como extra.
17. O override manual do staff (`PATCH /admin/agendamentos/{id}/fotos/{fotoId}/selecao`) passa pelas mesmas regras do cliente: valida limite do pacote, foto publicada/visível/numerada e bloqueia remoção de foto baixada/editada/comprada. Cada override gera log estruturado com agendamento, foto, usuário e total do pacote.
18. O resumo admin (`GET /admin/agendamentos/{id}/ecommerce`) expõe `pacoteQuantidadeFotos` para a UI desabilitar “Incluir no pacote” quando o limite já foi atingido.
19. O texto do hero fica em `galerias_ensaio.titulo_hero` (até 120 caracteres). Nulo/vazio faz o hero exibir o nome do cliente; a customização é independente do modo/fase e pode ser alterada a qualquer momento.
20. Ao reabrir uma seleção, o snapshot vira `CANCELADA` e a galeria volta para `SELECAO`, mas `selecionadaPacote` é preservado: as fotos escolhidas anteriormente continuam como rascunho editável. A resposta pública expõe `selecaoReaberta` para o cliente ser avisado.
21. `DELETE /galeria/{token}/rascunho` desmarca apenas as fotos do pacote (`selecionadaPacote = false`). Extras no carrinho não são removidos.

## 8. Galeria arquivada (V17)

Quando o expurgo de uma galeria finalizada remove os arquivos das fotos:

1. O estado é **derivado** de `foto.arquivosRemovidos` (galeria com fotos e todas arquivadas). Não há coluna em `galerias_ensaio`.
2. `GaleriaQueryService.buscarAgendamentoPorToken` responde **410 `GALERIA_ARQUIVADA`** para qualquer endpoint público da galeria (fotos, seleção, carrinho, compras, download e capa).
3. `GET /ecommerce/fotos/{fotoId}/watermarked|thumb` deixa de servir fotos arquivadas (`podeServirPublicamente`).
4. `DownloadService` valida a existência do arquivo e responde 410 quando ele foi expurgado.
5. `AdminGaleriaEnsaioController` expõe `arquivada`, `arquivadaEm` e `bytesLiberados`; o workspace mostra o selo/aviso e o link para o painel de armazenamento.
6. O frontend (`GaleriaClientePage`) trata o 410 e renderiza a tela "Esta galeria foi arquivada".
7. Comprovantes, contratos assinados (`uploads/propostas/*.pdf`) e todo o histórico comercial permanecem intactos.

## 9. Central de Galerias (V19)

O módulo passou a expor uma **fila operacional** do pós-ensaio, além da venda de extras.

### Modelo

- `EtapaGaleria` (`ecommerce/model`) é uma etapa **derivada**, nunca persistida, com precedência:
  arquivada → seleção ativa → sem fotos → fotos não publicadas → comercialização → aguardando seleção.
- `GaleriaPipelineEtapaResolver` concentra a regra pura (testada em `GaleriaPipelineEtapaResolverSpec`).
- `GaleriaPipelineQueryService` compõe `Agendamento`, `FotoEnsaio`, `SelecaoEnsaio`, `CompraExtra` e
  `GaleriaEnsaio` com queries agregadas por agendamento (sem `findAll()` e sem N+1).
- `galerias_ensaio.publicada_em` (V19) guarda a **primeira** publicação das fotos, usada para medir o
  tempo ensaio → publicação. Publicações posteriores não alteram a data.

### Endpoints

Staff (`ADMIN/FOTOGRAFO/EDITOR`):
- `GET /api/v1/admin/galerias/pipeline?etapa=&busca=&atrasadas=&page=&perPage=`
- `GET /api/v1/admin/galerias/pipeline/resumo`
- `GET /api/v1/admin/galerias/pipeline/export.csv?etapa=&busca=&atrasadas=` (CSV com BOM UTF-8 e separador `;`)

Staff (`ADMIN/FOTOGRAFO`):
- `GET /api/v1/admin/galerias/desempenho?meses=6` — SLA, funil, extras e série mensal
  (calculado por `GaleriaPerformanceCalculator`, com média apenas das amostras disponíveis).

Público:
- `GET /api/v1/ecommerce/galeria/{token}/status` — etapa e mensagem amigável, disponível mesmo com 0 fotos.

### Eventos e link do cliente

- `FotoService.publicar()` publica `FotosGaleriaPublicadasEvent`.
- `ecommerce.GaleriaPublicacaoListener` grava `galerias_ensaio.publicada_em`.
- `agenda.AgendamentoGaleriaPublicadaListener` **estende** `tokenExpiracao` para publicação + 180 dias
  sem rotacionar o token, preservando links já enviados. Corrige o caso em que o link (criado com a
  proposta, +15 dias) vencia antes de o staff publicar as fotos.

### Regras

1. Etapa não é persistida: a fila sempre reflete o estado real de agenda/foto/seleção/compra.
2. `diasNaEtapa` usa a data de realização, a publicação ou o envio da seleção, conforme a etapa.
3. `prazoEntrega` = (data de realização, ou data do ensaio) + `pacote.diasParaEntrega`; `atrasada`
   quando o prazo vence e a etapa não é terminal.
4. Pagamentos pendentes e extras aguardando edição são **pendências** (badges), não etapas: uma
   galeria pode estar “em edição” e ter uma compra aguardando confirmação ao mesmo tempo.
5. O endpoint público não expõe SLA interno, pagamentos ou dados do staff.

