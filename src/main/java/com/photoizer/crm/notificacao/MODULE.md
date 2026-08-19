# Módulo: Notificação

## 1. Responsabilidade
Cria e gerencia notificações do sistema para usuários (fotógrafos/equipe). É um módulo **puramente reativo** — as notificações nascem de eventos do módulo `agenda` (novo ensaio, ensaio realizado, pagamento final) consumidos pelo `NotificacaoEventListener`; nenhum outro módulo o importa. Expõe endpoints de consulta/gerenciamento por **userId** (ver dívida 7.1).

## 2. Estrutura
```
notificacao/
├── model/
│   ├── Notificacao.java       # Entidade (NÃO estende BaseEntity): userId, titulo, mensagem, link, tipo (enum), lida, createdAt
│   └── TipoNotificacao.java   # Enum: NOVO_ENSAIO, ENSAIO_REALIZADO, PAGAMENTO_FINAL, LEMBRETE_ENSAIO, REPASSE_FOTOGRAFO, SISTEMA
├── repository/
│   └── NotificacaoRepository.java # JpaRepository + findByUserIdOrderByCreatedAtDesc, countByUserIdAndLidaFalse
├── service/
│   └── NotificacaoService.java    # criar, listar, contarNaoLidas, marcarComoLida, marcarTodasComoLidas, limpar
├── event/
│   └── NotificacaoEventListener.java # Consome 3 eventos de agenda e cria notificações por fotógrafo
└── api/
    ├── NotificacaoController.java  # GET (listar, nao-lidas), PATCH (ler, ler-todas, limpar)
    └── NotificacaoResponse.java    # Record com static of() manual
```

## 3. Dependências Externas

### Módulos internos importados — **[VIOLAÇÕES Modulith]**
| Módulo | Onde | Uso |
|--------|------|-----|
| **agenda** | `NotificacaoEventListener` | eventos `AgendamentoCriadoEvent`, `AgendamentoRealizadoEvent`, `PagamentoFinalRegistradoEvent` (uso correto) |
| **agenda** | `NotificacaoEventListener` | **`AgendamentoRepository` + `AgendamentoFotografoRepository`** injetados para buscar agendamento/fotógrafos do evento — acesso direto a repositório de outro módulo |

> Nenhum módulo importa `notificacao` — é terminal **para os outros módulos**. Porém, ao buscar os dados que precisa, atravessa direto o repositório do `agenda`.

### Eventos consumidos
| Evento (agenda) | Ação |
|------------------|------|
| `AgendamentoCriadoEvent` | Para cada fotógrafo do agendamento cria "Novo Ensaio Agendado" (`NOVO_ENSAIO`) |
| `AgendamentoRealizadoEvent` | Para cada fotógrafo cria "Ensaio Realizado" (`ENSAIO_REALIZADO`) |
| `PagamentoFinalRegistradoEvent` | Para cada fotógrafo cria "Pagamento Final Recebido" (`PAGAMENTO_FINAL`) |

### Eventos publicados
Nenhum.

## 4. Fluxos Principais

### Fluxo 1: Notificar fotógrafos de um novo ensaio
1. `agenda` publica `AgendamentoCriadoEvent` com `agendamentoId`.
2. `NotificacaoEventListener.onAgendamentoCriado` (`:34-50`):
   - Busca o agendamento e os vínculos `AgendamentoFotografo` via **repositório do módulo agenda** (`:35,37`).
   - Para cada fotógrafo, chama `notificacaoService.criar` com link `/agenda/{id}`.
   - **Silenciosamente retorna** se agendamento não existir (`:36`).

### Fluxo 2/3: Ensaio realizado e pagamento final
- Mesmo padrão: `:52-69` e `:71-89` — busca links + agendamento via repositório do agenda, faz loop e cria notificação com link (`/agenda/{id}` ou `/minhas-financas`).

### Fluxo 4: Consulta e gerenciamento
- `GET /api/v1/notificacoes?userId=` → lista ordenada por `createdAt` desc; `GET /notificacoes/nao-lidas?userId=` → contagem.
- `PATCH /notificacoes/{id}/ler`, `PATCH /notificacoes/ler-todas?userId=`, `PATCH /notificacoes/limpar?userId=`.

## 5. Regras Específicas
1. **Identificador por `userId` vindo da requisição** — o controller não deriva o usuário do JWT (`SecurityContext`); confia no parâmetro (ver 7.1).
2. **Entidade sem `BaseEntity`**: `Notificacao` tem `id`/`createdAt` próprios, sem `updatedAt`/`createdBy` — única entidade fora do padrão (documentado no AGENTS.md).
3. **Sem paginação** em `listar`.
4. **Enum com valores mortos**: `LEMBRETE_ENSAIO`, `REPASSE_FOTOGRAFO` e `SISTEMA` nunca são criados — não existe job de lembretes nem notificação de repasse (o AGENTS.md cita "agenda lembretes" mas não há implementação).
5. **Listeners sem `@Transactional`**: cada `criar` é um `save` isolado; falha no meio do loop deixa notificações parciais.

## 6. Testes
Nenhum teste específico. Apenas `CrmApplicationTests` (smoke de contexto).

## 7. Dívidas Técnicas e Melhorias Recomendadas

### 7.1 Falha de ownership/segurança: `userId` por request param — **[CRÍTICO] P1**
- `NotificacaoController` aceita `userId` em `listar`/`nao-lidas`/`ler-todas`/`limpar` e `id` em `{id}/ler`; `SecurityConfig` permite `authenticated` (`SecurityConfig.java:80`). **Qualquer usuário autenticado pode ler/marcar/limpar notificações de outro usuário**.
- **Solução**: obter o usuário do `JwtAuthenticationFilter`/`SecurityContextHolder` (o `User` está no token) e forçar `userId = usuário logado`; validar ownership no `marcarComoLida` (a notificação deve pertencer ao usuário).

### 7.2 Listener atravessa repositório do agenda + risco de LAZY — **P1**
- `NotificacaoEventListener` injeta `AgendamentoRepository` e `AgendamentoFotografoRepository` (`:20-21`) e acessa `agendamento.getCliente().getNome()` (`:45,64,83`) fora de transação — `cliente` e `fotografo` são LAZY (risco de `LazyInitializationException`) e repetição de lookup em cada evento.
- **Solução**: os eventos de agenda devem carregar os dados necessários (ex.: `fotografoIds` + `clienteNome` + `dataHoraEnsaio` já resolvidos), ou usar `@Transactional` + query com JOIN FETCH; eliminar a dependência de repositório.

### 7.3 Três handlers com mesmo padrão duplicado — **P2**
- `onAgendamentoCriado`/`onAgendamentoRealizado`/`onPagamentoFinalRegistrado` repetem fetch + loop + `criar`.
- **Solução**: método helper `notificarFotografos(agendamentoId, titulo, mensagem, link, tipo)`.

### 7.4 Operações em massa ineficientes — **P2**
- `marcarTodasComoLidas` salva cada item em loop (`NotificacaoService:44-52`); `limpar` carrega tudo para `deleteAll` (`:54-57`); `listar` sem paginação.
- **Solução**: query de update bulk (`UPDATE ... SET lida=true WHERE userId`) e `deleteByUserId`, `Pageable`.

### 7.5 Exceção genérica — **P2**
- `marcarComoLida` lança `IllegalArgumentException` (`NotificacaoService:39`).
- **Solução**: `NotificacaoNaoEncontradaException` + hierarquia central `BusinessException`.

### 7.6 Enum com valores sem uso — **P3**
- `LEMBRETE_ENSAIO`, `REPASSE_FOTOGRAFO`, `SISTEMA` nunca criados — implementar os fluxos (job de lembretes com `@Scheduled`, notificação de repasse) ou remover.

### 7.7 DTO manual e sincronia dos eventos — **P3**
- `NotificacaoResponse.of` manual (MapStruct na fase 2); eventos agendados são processados sincronamente no dispatcher (recomendar `@Async`/`ApplicationEventMulticaster` para não atrasar a request).

## 8. Exemplos de arquivos afetados
- `NotificacaoController.java:30-62` — `userId` como `@RequestParam`, sem ownership (P1 7.1).
- `NotificacaoEventListener.java:20-21,35-89` — repositórios do agenda + acesso LAZY + padrão duplicado.
- `NotificacaoService.java:37-57` — `IllegalArgumentException` e operações em massa N+1.
- `Notificacao.java:17-53` — entidade fora do padrão BaseEntity.
- `TipoNotificacao.java:3-9` — valores `LEMBRETE_ENSAIO`/`REPASSE_FOTOGRAFO`/`SISTEMA` sem uso.
- `auth/config/SecurityConfig.java:80` — rota `authenticated` sem checagem de dono.