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
│   ├── AgendamentoService.java       # ~768 linhas: criação, listagem, atualização, status, reagendar, conflitos, pagamento final, partilha, fotografos
│   ├── AgendamentoFotografoService.java # ~208 linhas: repasses (pagar, cancelar, lote) + partilha
│   ├── RascunhoAgendamentoService.java  # ~92 linhas: salvar/buscar/deletar rascunho por usuário
│   └── CriarAgendamentoCommand.java      # Record com 27 campos (inclui MultipartFile)
├── api/
│   ├── AgendamentoController.java     # REST: POST (multipart ~28 @RequestParam), GET, PUT, PATCH /status, PATCH /reagendar, PATCH /destaque, POST /pagamento-final, GET /verificar-disponibilidade
│   ├── AgendamentoFotografoController.java # REST de repasses
│   ├── RascunhoAgendamentoController.java  # REST de rascunhos
│   ├── AgendamentoResponse.java       # Record com 30+ campos, 3 factories static of()
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
- `atualizarStatus(id, String novoStatus)` (`:235-253`): `valueOf` direto, **sem validação de transição válida** (aceita qualquer enum; ex.: `FINALIZADO → CONFIRMADO`). Apenas dispara eventos/filtra datas para `REALIZADO`/`CANCELADO`/`NO_SHOW`.
- `registrarPagamentoFinal` (`:412-442`): valida status ∈ {REALIZADO, AGUARDANDO_PAGAMENTO_FINAL}, exige comprovante, zera `valorRestante`, `EM_EDICAO`, publica evento.

### Fluxo 3: Repasses de Fotógrafos (partilha)
- `AgendamentoFotografoService`: adicionar/atualizar/remover/pagar/cancelar repasse (`:48-153`), com **validação de partilha** (`validarPartilha`) que soma custos via `DespesaService` e repasses ativos, garantindo que a soma não exceda a partilha.
- `AgendamentoService.calcularPartilhaFotografo` (`:541-565`) recalcula `valorPartilhaGlobal`/`valorLucroCrm` após cada mudança.

### Fluxo 4: Rascunho de Agendamento
- `RascunhoAgendamentoService.salvarRascunho` (`:23-82`) com **26 parâmetros posicionais**; upsert por `usuarioId` (1 rascunho por usuário).

### Fluxo 5: Materialização via Contrato
- `ContratoAprovadoEventListener` (`:25-34`) consome `ContratoAprovadoEvent` → `AgendamentoService.criarAgendamentoDeContrato` (`:444-539`) que duplica quase toda a lógica do Fluxo 1 e ainda grava `agendamentoId` no `Contrato` via `ContratoRepository` (cross-module).

## 5. Regras Específicas
1. **Controller com ~28 `@RequestParam`**: parsing manual e frágil; qualquer campo novo exige alteração em controller, command, service e entidade.
2. **Resolução de cliente com efeito colateral**: o service cria `Cliente` quando inexistente (e silenciosamente faz `catch` de `OrigemCliente.valueOf` → `OUTROS`, `:665-672`).
3. **Cálculo financeiro duplicado 3×**: `criarAgendamento` (`:125-133`), `atualizar` (`:336-342`), `criarAgendamentoDeContrato` (`:475-483`).
4. **`validarConflitoAgenda` duplicada em 2 sobrecargas** (`:705-767`), com lógica copiada.
5. **`atualizarStatus` sem state machine**: transições inválidas não são bloqueadas; `StatusAgendamento` é um enum sem comportamento.
6. **tokenGaleria**: UUID com expiração fixa de 15 dias (`:164`) — hardcoded.
7. **`listarAgendamentosCliente` faz 3 consultas ao módulo `foto` POR agendamento** (`:261-272`) — N+1 cross-module.
8. **`AgendamentoController` usa repo de `comissao` + `AgendamentoFotografoRepository` diretamente** (`:48-49`) — controller acessando repositories de outra/infra.
9. **`@Transactional` em nível de classe** em todos os services.
10. **Assinatura com MUITOS parâmetros**: `RascunhoAgendamentoService.salvarRascunho` (26 args), `CriarAgendamentoCommand` (27 campos, inclui `MultipartFile` — vaza dependência de web para dentro da camada de serviço).

## 6. Testes
Nenhum teste específico. Apenas `CrmApplicationTests` (smoke). Não há testes para fluxo de status, conflito, partilha ou repasses.

## 7. Dívidas Técnicas e Melhorias Recomendadas

### 7.1 `AgendamentoService` é god class (~768 linhas) — **P1**
- **Problema**: 16+ responsabilidades em um service (criar, atualizar, listar, status, reagendar, disponibilidade, pagamento, partilha, fotografos, contrato, resolução de cliente).
- **Solução (Clean Architecture + SRP)**:
  - Separar em services de aplicação: `AgendamentoQueryService` (buscas), `AgendamentoCommandService` (criação/atualização), `AgendamentoStatusLifecycle` (máquina de estados), `PartilhaService` (partilha/repasse), `DisponibilidadeService`.
  - Mover regras para **domínio**: cálculos de valor e transições de status para o próprio `Agendamento` (métodos `aplicarPagamentoFinal()`, `transicionarPara(novo)` — **State Pattern via enum**).

### 7.2 Status machine sem validação — **P1**
- `atualizarStatus` aceita qualquer status (`AgendamentoService.java:235-253`).
- **Solução**: encapsular transições no enum `StatusAgendamento` com método `transicoesValidas()` ou método `transicionarPara(StatusAgendamento)` na entidade que valida e lança exceção de domínio (`StatusInvalidoException`). Elimina o parâmetro String solto e centraliza as regras (polimorfismo no enum, sem herança).

### 7.3 Duplicação de cálculo financeiro — **P1**
- Cálculo de `valorTotal`/`valorEntradaExigido`/`valorRestante`/`valorTotalFinal` repetido 3× (`criar`, `atualizar`, `criarAgendamentoDeContrato`).
- **Solução**: extrair um `CalculadoraFinanceira` (componente de domínio) com entrada `(pacote, taxa, percentual)`; ou um método `Agendamento.recalcularValores(pacote, taxaDeslocamento, percentualEntrada)`. Testável isoladamente.

### 7.4 Violações Modulith (services/repos de outros módulos) — **P1**
- `DespesaService` e `ConfiguracaoService` chamados diretamente em `AgendamentoService` (`:69-70`); `FotoEnsaioRepository` (`:68`); `IndicacaoRepository` no controller (`:48`); `ContratoRepository` no listener (`:17`).
- **Solução**: 
  - chamada de custos (despesa) e foto → expor via **eventos de consulta** ou um módulo de API pública de leitura; 
  - partilha de custos → mover responsabilidade de custos para dentro do fluxo de despesa (evento `PartilhaRequerida`) ou criar porta/interface no domínio (Dependency Inversion) implementada pelo módulo dono dos dados.

### 7.5 Vazamento de web na camada de serviço — **P1**
- `CriarAgendamentoCommand` contém `MultipartFile` (`:32`) e `RascunhoAgendamentoService.salvarRascunho` recebe 26 args.
- **Solução (Clean Architecture)**: definir `AgendamentoCommand`/registro de eventos com dados já processados (ex.: `urlComprovante`, `nomeArquivo`) — o upload deve acontecer na camada de infraestrutura (controller/gateway), não no domínio. Reduzir args com command objects agrupados (`DadosCliente`, `DadosEnsayo`, `Repasses`).

### 7.6 N+1 e queries por item em `listarAgendamentosCliente` — **P2**
- 3 `count(...)` ao módulo `foto` por agendamento (`AgendamentoService.java:264-268`).
- **Solução**: agregar contagens em uma única consulta agrupada (JPQL `GROUP BY agendamentoId`) no módulo `foto` e expor via API de consulta/evento; ou usar `@EntityGraph`/DTO projection com contagens.

### 7.7 Nome enganoso `findByLocalAndDataBetweenExcludingId` — **P3**
- A JPQL ignora `local` (`AgendamentoRepository.java:39-45`). Renomear método (`findActiveBetweenExcludingId`) e corrigir o chamador.

### 7.8 Rivals sem lógica de reaproveitamento — **P2**
- `AgendamentoFotografoService.calcularValor` e `AgendamentoService.valorRepasseEfetivo` são a **mesma** regra duplicada em dois services (`AgendamentoFotografoService.java:174-181` vs `AgendamentoService.java:629-636`). Extrair para componente único de domínio.

### 7.9 DTOs manuais — **P2**
- `AgendamentoResponse` com 3 factories e ~200 linhas de `of()` manual (`AgendamentoResponse.java:76-205`).
- **Solução**: **MapStruct** com `@Mapping(target="valorPacote", expression=...)`, `@Context` para comissão/indicador e componentiza o nested `FotografoNoAgendamento` (usando `AgendamentoFotografoMapper`).

### 7.10 Exceções — **P2**
- 6 classes quase idênticas (ver `shared/MODULE.md §7.3`): consolidar em hierarquia central; `FotografoNaoEncontradoException` deveria residir onde `User` pertence (auth) ou ser proveito pela hierarquia `NotFoundException`.

### 7.11 Lombok/setters expostos — **P2**
- `@Setter` de classe em `Agendamento`/`RascunhoAgendamento`/`AgendamentoFotografo` permite mutação arbitrária. Restringir com `@Setter(AccessLevel.PRIVATE)` + métodos de domínio (transições e pagamentos) — ver `shared/MODULE.md §7.9`.

### 7.12 Indices e tokenGaleria — **P3**
- `tokenGaleria` é `unique` mas sem índice explícito? (verificar agendamento). Exportar expiração do token para `Config` (já existe `prazo_expiracao_token_galeria_dias` mas o código usa 15 hardcoded, `AgendamentoService.java:164`).

## 8. Exemplos de arquivos afetados
- `AgendamentoService.java` (:69-70, :125-133, :235-253, :261-272, :336-342, :444-539, :705-767) — god class, duplicação, cross-module e state machine; `AgendamentoController.java:70-146` — 28 `@RequestParam`; `CriarAgendamentoCommand.java:32` — `MultipartFile` no command; `RascunhoAgendamentoService.java:23-82` — 26 args; `AgendamentoResponse.java:76-205` — mappers manuais; `AgendamentoRepository.java:39-45` — query mal nomeada.
