# Módulo: Agenda

## 1. Responsabilidade
Gerencia o ciclo de vida completo de ensaios fotográficos (agendamentos), repasses de fotógrafos, rascunhos de agendamento e a materialização de agendamentos a partir de contratos aprovados. É o módulo central — coordena clientes, pacotes, financeiro, edição e comissões através de **Application Events**. (O submódulo `Tarefa` foi removido do código atual.)

## 2. Estrutura Interna
```
agenda/
├── model/
│   ├── Agendamento.java              # Entidade JPA principal (~40 campos, extends BaseEntity)
│   ├── AgendamentoFotografo.java     # Entidade JPA (extends BaseEntity, @ManyToOne Agendamento + User, unique (agendamento,fotografo))
│   ├── RascunhoAgendamento.java      # Entidade JPA (extends BaseEntity, index usuario_id, ~25 campos String)
│   ├── StatusAgendamento.java        # Enum: CONFIRMADO, REALIZADO, AGUARDANDO_PAGAMENTO_FINAL, EM_EDICAO, SELECAO_DAS_FOTOS, FOTOS_ENVIADAS_PARA_SELECAO, FOTOS_ENTREGUES, FINALIZADO, CANCELADO, NO_SHOW
│   └── RepasseStatus.java            # Enum: PENDENTE, PAGO, CANCELADO
├── repository/
│   ├── AgendamentoRepository.java    # JpaRepository + JpaSpecificationExecutor (~9 queries customizadas)
│   ├── AgendamentoFotografoRepository.java # JpaRepository + JOIN FETCH e SUMs agregados
│   └── RascunhoAgendamentoRepository.java  # JpaRepository
├── service/
│   ├── AgendamentoService.java       # ~532 linhas: criação, listagem, atualização, resolução de cliente, materialização via contrato
│   ├── AgendamentoStatusLifecycle.java# ~125 linhas: máquina de estados (status, reagendar, destaque, pagamento final) + eventos [Fase 2]
│   ├── AgendamentoValoresCalculator.java # ~69 linhas: cálculo financeiro único (novo/atualização) + valorRepasseEfetivo [Fase 2]
│   ├── PartilhaService.java          # ~79 linhas: calcular/validar partilha de fotógrafos (usa DespesaService) [Fase 2]
│   ├── DisponibilidadeService.java   # ~139 linhas: verificarDisponibilidade + validarConflitoAgenda (com/sem excluirId) [Fase 2]
│   ├── AgendamentoFotografoService.java # ~177 linhas: repasses (pagar, cancelar, lote) + partilha
│   ├── RascunhoAgendamentoService.java  # ~92 linhas: salvar/buscar/deletar rascunho por usuário
│   └── CriarAgendamentoCommand.java      # Record com 27 campos (inclui MultipartFile)
├── api/
│   ├── AgendamentoController.java     # REST: POST (multipart ~28 @RequestParam), GET, PUT, PATCH /status, PATCH /reagendar, PATCH /destaque, POST /pagamento-final, GET /verificar-disponibilidade
│   ├── AgendamentoFotografoController.java # REST de repasses
│   ├── RascunhoAgendamentoController.java  # REST de rascunhos
│   ├── AgendamentoMapper.java         # MapStruct multi-source: agendamento + fotografos + valorComissao + indicador + statusComissao [Fase 2]
│   ├── RascunhoAgendamentoMapper.java # MapStruct (AgendamentoResponse/RascunhoAgendamentoResponse) [Fase 2]
│   ├── AgendamentoResponse.java       # Record com 30+ campos, sem factories of() (migrado p/ mappers)
│   ├── AtualizarAgendamentoRequest.java  # Record com @Valid + nested FotografoRepasse
│   └── DisponibilidadeResponse.java      # Record: disponivel boolean + List<Conflito>
├── event/
│   ├── AgendamentoCriadoEvent.java        # Publicado ao criar
│   ├── AgendamentoConfirmadoEvent.java    # Publicado ao criar + ao reagendar
│   ├── AgendamentoRealizadoEvent.java     # Publicado ao marcar como REALIZADO
│   ├── AgendamentoCanceladoEvent.java     # Publicado ao cancelar/noShow
│   └── PagamentoFinalRegistradoEvent.java # Publicado ao registrar pagamento final
├── listener/
│   └── ContratoAprovadoEventListener.java # Consome ContratoAprovadoEvent → materializa Agendamento
└── exception/
    ├── AgendamentoNaoEncontradoException.java
    ├── AgendamentoNoPassadoException.java
    ├── ConflitoDeAgendaException.java
    ├── EditorNaoEncontradoException.java
    ├── EnsaioNaoFinalizadoException.java
    └── FotografoNaoEncontradoException.java
```

## 3. Dependências Externas

### Módulos internos importados diretamente (violam o isolamento de services do Modulith)
- **cliente** → `ClienteRepository`, `Cliente`, `OrigemCliente`, `ClienteNaoEncontradoException` (resolução de cliente em `AgendamentoService`)
- **pacote** → `PacoteRepository`, `Pacote`, `PacoteNaoEncontradoException`, `PacoteInativoException`
- **auth** → `UserRepository`, `Papel`
- **shared** → `BaseEntity`, `TipoRepasse`, `FileStorageService`, `ApplicationEventPublisher`
- **config** → `ConfiguracaoService` (percentualEntrada, taxaDeslocamentoPadrao) — **service de outro módulo**
- **despesa** → `DespesaService` (somarCustosTodosFotografos em partilha) — **service de outro módulo**
- **foto** → `FotoEnsaioRepository`, `StatusFoto` (contagem de fotos do cliente) — **repository de outro módulo**
- **comissao** → `IndicacaoRepository` (usado no `AgendamentoController`) — **repository de outro módulo**
- **contrato** → `ContratoAprovadoEvent` (consumir) + `ContratoRepository` usado no `ContratoAprovadoEventListener` — **repository de outro módulo**

### Acoplamento reverso (outros módulos importam o `agenda`)
- **financeiro** → `FinanceiroService` importa `AgendamentoMapper` (agenda) para converter `Agendamento` em `AgendamentoResponse` [Fase 2]
- **cliente** → `ClienteController` importa `AgendamentoMapper` (agenda) para o mesmo fim [Fase 2]

> Sem testes Modulith de verificação no projeto; injeção cross-module de mappers não quebra o build.

### Eventos publicados (consumidores em outros módulos)
| Evento | Consumidores |
|--------|-------------|
| AgendamentoCriadoEvent | IndicacaoListener, DocumentoEventListener |
| AgendamentoConfirmadoEvent | DocumentoEventListener |
| AgendamentoRealizadoEvent | FinanceiroEventListener |
| AgendamentoCanceladoEvent | IndicacaoListener |
| PagamentoFinalRegistradoEvent | EdicaoListener, IndicacaoListener |

### Eventos consumidos
| Evento | Origem | Handler |
|--------|--------|---------|
| ContratoAprovadoEvent | contrato | `ContratoAprovadoEventListener` → cria Agendamento via `criarAgendamentoDeContrato` |

## 4. Fluxos Principais

### Fluxo 1: Criação de Agendamento (multipart)
1. `POST /api/v1/agendamentos` (multipart) → `AgendamentoController.criar()` com **~28 `@RequestParam`** (parsing manual de UUID, BigDecimal, LocalDateTime e JSON de `fotografos` via `JsonMapper`, `AgendamentoController.java:70-146`).
2. `AgendamentoService.criarAgendamento(CriarAgendamentoCommand)`:
   - Resolve cliente em 3 níveis (`clienteId` > `telefone` > cria novo) — efeito colateral de criação de `Cliente` (`:638-692`).
   - Valida pacote ativo, editor, data não passada.
   - Calcula valores (duplicado com `atualizar` e `criarAgendamentoDeContrato`).
   - Valida conflito de agenda.
   - Salva comprovante e cria `Agendamento` novamente com `tokenGaleria` (15 dias), status `CONFIRMADO`, cria fotógrafos e recalcula partilha.
   - Publica `AgendamentoCriadoEvent` + `AgendamentoConfirmadoEvent`.

### Fluxo 2: Ciclo de Vida (Status Machine)
```
CONFIRMADO ──realizar──▶ AGUARDANDO_PAGAMENTO_FINAL ──pagarFinal──▶ EM_EDICAO
    │                        │                                           │
    ├─reagendar─┐            │                              enviarSelecao│
    └─cancelar──┼─▶ CANCELADO│                                           ▼
                │            │                               FOTOS_ENVIADAS_PARA_SELECAO
                └──▶ NO_SHOW  │                                           │
                          (via PATCH /status)             confirmarEntrega│
                                                                           ▼
                                                                 FOTOS_ENTREGUES
                                                                           │
                                                                   finalizar│
                                                                           ▼
                                                                    FINALIZADO
```
- `atualizarStatus(id, String novoStatus)` → delegado a `AgendamentoStatusLifecycle.atualizarStatus` → `agendamento.transicionarPara(status)`: `valueOf` direto, **sem validação de transição válida** (aceita qualquer enum; ex.: `FINALIZADO → CONFIRMADO`). Eventos publicados **após o `save`** (consistência em caso de rollback/falha de persistência). Apenas dispara eventos para `REALIZADO`/`CANCELADO`/`NO_SHOW` — comportamento mantido (decisão: encapsular sem bloquear).
- `registrarPagamentoFinal` → `AgendamentoStatusLifecycle.registrarPagamentoFinal` → `agendamento.aplicarPagamentoFinal(url)`: valida status ∈ {REALIZADO, AGUARDANDO_PAGAMENTO_FINAL}, exige comprovante, zera `valorRestante`, `EM_EDICAO`, publica evento.

### Fluxo 3: Repasses de Fotógrafos (partilha)
- `AgendamentoFotografoService`: adicionar/atualizar/remover/pagar/cancelar repasse, delegando validação de partilha e método de domínio agrupados (`atualizarRepasse`/`pagar`/`cancelar` em `AgendamentoFotografo`) a `PartilhaService.calcularPartilhaFotografo/validarPartilha` (soma custos via `DespesaService` e repasses ativos, garantindo que a soma não exceda a partilha).
- `AgendamentoService` recalcula `valorPartilhaGlobal`/`valorLucroCrm` via `AgendamentoValoresCalculator` após cada mudança.

### Fluxo 4: Rascunho de Agendamento
- `RascunhoAgendamentoService.salvarRascunho` (`:23-82`) com **26 parâmetros posicionais**; upsert por `usuarioId` (1 rascunho por usuário).

### Fluxo 5: Materialização via Contrato
- `ContratoAprovadoEventListener` consome `ContratoAprovadoEvent` → `AgendamentoService.criarAgendamentoDeContrato` que **reutiliza o Template Method `criarAgendamentoBase`** (mesmo fluxo de persistência/fotógrafos/partilha/eventos do Fluxo 1, com valores vindos do evento via `ValoresAgendamento`) e ainda grava `agendamentoId` no `Contrato` via `ContratoRepository` (cross-module).

## 5. Regras Específicas
1. **Controller com ~28 `@RequestParam`**: parsing manual e frágil; qualquer campo novo exige alteração em controller, command, service e entidade.
2. **Resolução de cliente com efeito colateral**: o service cria `Cliente` quando inexistente (e silenciosamente faz `catch` de `OrigemCliente.valueOf` → `OUTROS`).
3. **Cálculo financeiro unificado** [Fase 2]: `AgendamentoValoresCalculator` é a fonte única de `novo`/`atualização` e `valorRepasseEfetivo`; não há mais 3 cópias (ver dívida 7.3).
4. **Conflito de agenda consolidado** [Fase 2]: `DisponibilidadeService.validarConflitoAgenda(pacote, dataHora, duracao, local[, excluirId])` único, sem cópias de lógica.
5. **Status sem validação de transição**: `AgendamentoStatusLifecycle`/`transicionarPara` centralizam a mudança, mas transições inválidas ainda não são bloqueadas (encapsulado, sem bloquear — decisão mantida).
6. **tokenGaleria**: UUID com expiração fixa de 15 dias — hardcoded (ver 7.12).
7. **`listarAgendamentosCliente` faz 3 consultas ao módulo `foto` POR agendamento** — N+1 cross-module (ver 7.6).
8. **`AgendamentoController` usa repo de `comissao` + `AgendamentoFotografoRepository` diretamente** — controller acessando repositories de outra/infra.
9. **`@Transactional` em nível de classe** em todos os services.
10. **Assinatura com MUITOS parâmetros**: `RascunhoAgendamentoService.salvarRascunho` (26 args), `CriarAgendamentoCommand` (27 campos, inclui `MultipartFile` — vaza dependência de web para dentro da camada de serviço).

## 6. Testes
Nenhum teste específico no módulo `agenda`. Tests existem em outros módulos que usam o agenda: `FinanceiroServiceTest`, `DashboardServiceTest`, `FinanceiroDashboardServiceTest` (48 testes, todos verdes). Não há testes para fluxo de status, conflito, partilha ou repasses.

## 7. Dívidas Técnicas e Melhorias Recomendadas

> Status após Fase 2 (refactor de extração de services + encapsulamento de domínio + MapStruct). Resolvidos ⇒ ✅; parciais ⇒ ◐; pendentes ⇒ 🔴.

### 7.1 `AgendamentoService` é god class (~768 linhas) — **P1** · ◐ parcial
- **Problema**: 16+ responsabilidades em um service.
- **Fase 2**: extraídos `AgendamentoStatusLifecycle` (status/reagendar/destaque/pagamento), `PartilhaService` (partilha/repasse), `DisponibilidadeService` (conflito/disponibilidade) e `AgendamentoValoresCalculator`. `AgendamentoService` caiu para ~532 linhas.
- **Fase 2 (2º refactor)**: aplicado **Template Method** (`criarAgendamentoBase` + record `DadosNovoAgendamento`) — `criarAgendamento` e `criarAgendamentoDeContrato` (~85% duplicadas) agora compartilham um único fluxo de persistência/validação/fotógrafos/partilha/eventos; valores do contrato passam a trafegar como `ValoresAgendamento` (sem cálculo manual).
- **Restam**: criação (multipart) ainda no service; resolução de cliente com efeito colateral.

### 7.2 Status machine sem validação — **P1** · ◐ parcial
- **Fase 2**: `Agendamento.transicionarPara(StatusAgendamento)` centraliza a mudança (seta `dataRealizacao` em REALIZADO); `AgendamentoStatusLifecycle.atualizarStatus` delega para ele, com eventos.
- **Restam**: transições inválidas continuam aceitas (decisão do usuário: **encapsular sem bloquear** — comportamento de negócio preservado). Próximo passo opcional: `StatusInvalidoException` + validação no enum.

### 7.3 Duplicação de cálculo financeiro — **P1** · ✅ resolvido
- **Fase 2**: `AgendamentoValoresCalculator.calcularValoresAgendamento(...)` (novo/atualização) + `calcularValorRepasse`/`valorRepasseEfetivo` como fonte única (testável isoladamente).

### 7.4 Violações Modulith (services/repos de outros módulos) — **P1** · 🔴 pendente
- `DespesaService`/`ConfiguracaoService`/`FotoEnsaioRepository`/`IndicacaoRepository`/`ContratoRepository` ainda importados. Além disso, **novo acoplamento reverso** (Fase 2): `financeiro.FinanceiroService` e `cliente.ClienteController` importam `AgendamentoMapper` do agenda.
- **Solução**: eventos de consulta/facades públicas; mover custos para fluxo de despesa; porta/interface no domínio (Dependency Inversion).

### 7.5 Vazamento de web na camada de serviço — **P1** · 🔴 pendente
- `CriarAgendamentoCommand` contém `MultipartFile`; `RascunhoAgendamentoService.salvarRascunho` recebe 26 args; `AgendamentoStatusLifecycle.registrarPagamentoFinal` recebe `MultipartFile`.
- **Solução**: command objects com dados já processados (`urlComprovante`, `nomeArquivo`); upload na camada de infraestrutura.

### 7.6 N+1 e queries por item em `listarAgendamentosCliente` — **P2** · 🔴 pendente
- 3 `count(...)` ao módulo `foto` por agendamento (`AgendamentoService.listarAgendamentosCliente`).
- **Solução**: agregar contagens em consulta agrupada (JPQL `GROUP BY agendamentoId`) no módulo `foto`; ou `@EntityGraph`/DTO projection.

### 7.7 Nome enganoso `findByLocalAndDataBetweenExcludingId` — **P3** · ✅ resolvido
- **Fase 2**: renomeado p/ `findActiveByLocalAndDataBetween` e `findActiveBetweenExcludingId` (JPQL ignora `local` apenas na versão `ExcludingId`; ambos renomeados para refletir que buscam agendamentos ativos).

### 7.8 Regras de repasse duplicadas — **P2** · ✅ resolvido
- **Fase 2**: `AgendamentoFotografoService.calcularValor` e `AgendamentoService.valorRepasseEfetivo` (duplicata) unificadas em `AgendamentoValoresCalculator.calcularValorRepasse`/`valorRepasseEfetivo`.

### 7.9 DTOs manuais — **P2** · ✅ resolvido (com observação)
- **Fase 2**: **MapStruct** adotado (`pom.xml`: `mapstruct 1.6.3` + `lombok-mapstruct-binding 0.2.0`). Criados `AgendamentoMapper` (multi-source com `@Context`/expressões preservando `valorPacote`/`saldoDevedor`/`status`) e `RascunhoAgendamentoMapper`. Factories `of()` removidas de `AgendamentoResponse`/`RascunhoAgendamentoResponse`.
- **Observação**: a resposta continua um record com 30+ campos; `AgendamentoMapper` é multi-source `(agendamento, fotografos, valorComissao, indicadorNome, statusComissao)` e virou acoplamento reverso p/ financeiro/cliente (ver 7.4).

### 7.10 Exceções — **P2** · 🔴 pendente
- 6 classes quase idênticas: consolidar em hierarquia central (`BusinessException`); `FotografoNaoEncontradoException` deveria residir onde `User` pertence.

### 7.11 Lombok/setters expostos — **P2** · ◐ parcial
- **Fase 2**: `AgendamentoFotografo` com `@Setter(AccessLevel.PRIVATE)` + métodos de domínio (`atualizarRepasse`, `pagar`, `cancelar`); `Agendamento` ganhou métodos de domínio (`transicionarPara`, `reagendar`, `aplicarPagamentoFinal`, `alternarDestaque`).
- **Restam**: `@Setter` de classe continua público em `Agendamento` e `RascunhoAgendamento` (mutação arbitrária ainda possível). Restringir p/ PRIVATE e migrar todas as escritas para métodos de domínio.

### 7.12 Indices e tokenGaleria — **P3** · 🔴 pendente
- `tokenGaleria` é `unique` sem índice explícito? (verificar); expiração do token hardcoded em 15 dias (usar `prazo_expiracao_token_galeria_dias` da config).

## 8. Exemplos de arquivos afetados
- **Fase 2 (novos)**: `service/AgendamentoStatusLifecycle.java`, `service/PartilhaService.java`, `service/DisponibilidadeService.java`, `service/AgendamentoValoresCalculator.java`, `api/AgendamentoMapper.java`, `api/RascunhoAgendamentoMapper.java`.
- **Fase 2 (alterados)**: `service/AgendamentoService.java` (delegação; ~532 linhas), `service/AgendamentoFotografoService.java` (PartilhaService + domínio), `model/Agendamento.java` (métodos de domínio), `model/AgendamentoFotografo.java` (`@Setter(PRIVATE)` + domínio), `repository/AgendamentoRepository.java` (queries renomeadas), `api/AgendamentoController.java` + `api/RascunhoAgendamentoController.java` (lifecycle/disponibilidade/mappers), `api/AgendamentoResponse.java` + `api/RascunhoAgendamentoResponse.java` (sem `of()`), `pom.xml` (MapStruct 1.6.3).
- **Pendências**: `service/AgendamentoService.java` (criação/criação-por-contrato/resolução de cliente), `service/CriarAgendamentoCommand.java` (`MultipartFile`), `service/RascunhoAgendamentoService.java` (26 args), `api/AgendamentoController.java` (~28 `@RequestParam`, `IndicacaoRepository` direto), `model/StatusAgendamento.java` (sem validação de transições), `model/Agendamento.java`/`RascunhoAgendamento.java` (`@Setter` público).
