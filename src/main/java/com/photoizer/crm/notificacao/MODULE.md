# Módulo: Notificação

## 1. Responsabilidade
Cria e gerencia notificações do sistema para usuários (fotógrafos/equipe). É um módulo **puramente reativo** — as notificações nascem de eventos do módulo `agenda` (novo ensaio, ensaio realizado, pagamento final) consumidos pelo `NotificacaoEventListener`; nenhum outro módulo o importa. Expõe endpoints de consulta/gerenciamento autenticados via JWT.

## 2. Estrutura
```
notificacao/
├── model/
│   ├── Notificacao.java       # Entidade (NÃO estende BaseEntity): userId, titulo, mensagem, link, tipo (enum), lida, createdAt
│   └── TipoNotificacao.java   # Enum: NOVO_ENSAIO, ENSAIO_REALIZADO, PAGAMENTO_FINAL, LEMBRETE_ENSAIO, REPASSE_FOTOGRAFO, SISTEMA
├── repository/
│   └── NotificacaoRepository.java # JpaRepository + findByUserIdOrderByCreatedAtDesc, countByUserIdAndLidaFalse
├── service/
│   └── NotificacaoService.java    # criar, listar, contarNaoLidas, marcarComoLida (com ownership), marcarTodasComoLidas, limpar
├── event/
│   └── NotificacaoEventListener.java # Consome 3 eventos enriquecidos de agenda e cria notificações por fotógrafo
├── exception/
│   ├── NotificacaoNaoEncontradaException.java       # 404
│   └── NotificacaoNaoPertenceAoUsuarioException.java # 403
└── api/
    ├── NotificacaoController.java  # GET (listar, nao-lidas), PATCH (ler, ler-todas, limpar) — via @AuthenticationPrincipal
    └── NotificacaoResponse.java    # Record com static of() manual
```

## 3. Dependências Externas

### Módulos internos importados
| Módulo | Onde | Uso |
|--------|------|-----|
| **agenda** | `NotificacaoEventListener` | eventos `AgendamentoCriadoEvent`, `AgendamentoRealizadoEvent`, `PagamentoFinalRegistradoEvent` (uso correto — dados enriquecidos pelo publisher) |

> Nenhum módulo importa `notificacao` — é terminal **para os outros módulos**. A violação Modulith anterior (acesso a repositórios do agenda) foi resolvida via **Event Enrichment**.

### Eventos consumidos
| Evento (agenda) | Dados enriquecidos | Ação |
|------------------|-------------------|------|
| `AgendamentoCriadoEvent` | `clienteNome`, `fotografoIds`, `dataHoraEnsaio` | Para cada fotógrafo cria "Novo Ensaio Agendado" (`NOVO_ENSAIO`) |
| `AgendamentoRealizadoEvent` | `clienteNome`, `fotografoIds` | Para cada fotógrafo cria "Ensaio Realizado" (`ENSAIO_REALIZADO`) |
| `PagamentoFinalRegistradoEvent` | `clienteNome`, `fotografoIds` | Para cada fotógrafo cria "Pagamento Final Recebido" (`PAGAMENTO_FINAL`) |

### Eventos publicados
Nenhum.

## 4. Fluxos Principais

### Fluxo 1: Notificar fotógrafos de um novo ensaio
1. `agenda` publica `AgendamentoCriadoEvent` com `agendamentoId`, `clienteNome`, `fotografoIds`, `dataHoraEnsaio`.
2. `NotificacaoEventListener.onAgendamentoCriado`:
   - Usa `fotografoIds` e `clienteNome` diretamente do evento (sem acesso a repositório).
   - Chama helper `notificarFotografos()` que cria notificação para cada fotógrafo.

### Fluxo 2/3: Ensaio realizado e pagamento final
- Mesmo padrão: consome dados enriquecidos do evento, usa helper `notificarFotografos()`.

### Fluxo 4: Consulta e gerenciamento (autenticado)
- `GET /api/v1/notificacoes` → lista do usuário autenticado (via `@AuthenticationPrincipal`).
- `GET /api/v1/notificacoes/nao-lidas` → contagem do usuário autenticado.
- `PATCH /api/v1/notificacoes/{id}/ler` → marca como lida com **validação de ownership**.
- `PATCH /api/v1/notificacoes/ler-todas` → marca todas como lidas do usuário autenticado.
- `PATCH /api/v1/notificacoes/limpar` → limpa notificações do usuário autenticado.

## 5. Regras Específicas
1. **Identificador por `@AuthenticationPrincipal`** — o controller extrai o userId do JWT (`SecurityContext`), não aceita parâmetro externo. Cada usuário só acessa suas próprias notificações.
2. **Ownership check em `marcarComoLida`** — valida que a notificação pertence ao usuário autenticado; caso contrário, lança `NotificacaoNaoPertenceAoUsuarioException` (403).
3. **Entidade sem `BaseEntity`**: `Notificacao` tem `id`/`createdAt` próprios, sem `updatedAt`/`createdBy` — única entidade fora do padrão (documentado no AGENTS.md).
4. **Sem paginação** em `listar`.
5. **Enum com valores mortos**: `LEMBRETE_ENSAIO`, `REPASSE_FOTOGRAFO` e `SISTEMA` nunca são criados — não existe job de lembretes nem notificação de repasse.
6. **Listeners sem `@Transactional`**: cada `criar` é um `save` isolado; falha no meio do loop deixa notificações parciais.

## 6. Testes
Nenhum teste específico. Apenas `CrmApplicationTests` (smoke de contexto).

## 7. Dívidas Técnicas e Melhorias Recomendadas

### ~~7.1 Falha de ownership/segurança: `userId` por request param~~ — **[RESOLVIDO] P1**
- **Resolvido**: `NotificacaoController` agora usa `@AuthenticationPrincipal` para extrair o userId do JWT. `marcarComoLida` valida ownership.

### ~~7.2 Listener atravessa repositório do agenda + risco de LAZY~~ — **[RESOLVIDO] P1**
- **Resolvido**: Eventos de agenda são enriquecidos (Event Enrichment) com `clienteNome`, `fotografoIds`. `NotificacaoEventListener` não mais injeta repositórios do agenda.

### ~~7.3 Três handlers com mesmo padrão duplicado~~ — **[RESOLVIDO] P1**
- **Resolvido**: Método helper `notificarFotografos()` elimina duplicação dos 3 handlers.

### ~~7.4 Operações em massa ineficientes~~ — **[RESOLVIDO] P2**
- **Resolvido**: `marcarTodasComoLidas` usa query bulk `@Modifying`. `limpar` usa `deleteByUserId`. `listar` suporta paginação via `Pageable`.

### ~~7.5 Exceção genérica~~ — **[RESOLVIDO] P1**
- **Resolvido**: `NotificacaoNaoEncontradaException` (404) e `NotificacaoNaoPertenceAoUsuarioException` (403) criadas. Registradas no `GlobalExceptionHandler`.

### 7.6 Enum com valores sem uso — **P3**
- `LEMBRETE_ENSAIO`, `REPASSE_FOTOGRAFO`, `SISTEMA` nunca criados — implementar os fluxos (job de lembretes com `@Scheduled`, notificação de repasse) ou remover.

### 7.7 DTO manual e sincronia dos eventos — **P3**
- `NotificacaoResponse.of` manual (MapStruct na fase 2); eventos agendados são processados sincronamente no dispatcher (recomendar `@Async`/`ApplicationEventMulticaster` para não atrasar a request).

### ~~7.8 Listener sem @Transactional~~ — **[RESOLVIDO] P2**
- **Resolvido**: `@Transactional` adicionado em cada `@EventListener` para garantir atomicidade na criação de notificações.

## 8. Exemplos de arquivos afetados

### Refatoração P1 (segurança + Modulith)
- `NotificacaoController.java` — `@AuthenticationPrincipal` substitui `@RequestParam userId`.
- `NotificacaoEventListener.java` — removidas injeções de `AgendamentoRepository`/`AgendamentoFotografoRepository`; helper `notificarFotografos()`.
- `NotificacaoService.java` — `marcarComoLida(id, userId)` com ownership check; exceções de domínio.
- `NotificacaoNaoEncontradaException.java` — novo.
- `NotificacaoNaoPertenceAoUsuarioException.java` — novo.
- `AgendamentoCriadoEvent.java` — campos `clienteNome`, `fotografoIds` adicionados.
- `AgendamentoRealizadoEvent.java` — campos `clienteNome`, `fotografoIds` adicionados.
- `PagamentoFinalRegistradoEvent.java` — campos `clienteNome`, `fotografoIds` adicionados.
- `AgendamentoService.java` — publicação do `AgendamentoCriadoEvent` enriquecido; null-safe.
- `AgendamentoStatusLifecycle.java` — injeção de `AgendamentoFotografoRepository`; publicação de eventos enriquecidos; null-safe.
- `GlobalExceptionHandler.java` — handlers para `NotificacaoNaoEncontradaException` (404) e `NotificacaoNaoPertenceAoUsuarioException` (403).

### Correções pós-implementação (P2)
- `NotificacaoEventListener.java` — `@Transactional` em cada `@EventListener` para atomicidade.
- `NotificacaoRepository.java` — queries bulk: `marcarTodasComoLidas` (`@Modifying`), `deleteByUserId`.
- `NotificacaoService.java` — `marcarTodasComoLidas` e `limpar` usam queries bulk; `listar` suporta `Pageable`.
- `NotificacaoController.java` — `listar` aceita `page`/`size` params; retorna `Page<NotificacaoResponse>`.
- `NotificacaoResponse.java` — `userId` removido (redundante com JWT).
