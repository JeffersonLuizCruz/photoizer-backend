# Módulo: Agenda

## 1. Responsabilidade
Gerencia o ciclo de vida completo de ensaios fotográficos (agendamentos) e tarefas de pós-produção. É o módulo central — coordena clientes, pacotes, financeiro, edição e comissões através de **Application Events**.

## 2. Estrutura Interna
```
agenda/
├── model/
│   ├── Agendamento.java              # Entidade JPA principal (~40 campos, extends BaseEntity)
│   ├── StatusAgendamento.java        # Enum: CONFIRMADO, REALIZADO, AGUARDANDO_PAGAMENTO_FINAL, EM_EDICAO, SELECAO_DAS_FOTOS, FOTOS_ENVIADAS_PARA_SELECAO, FOTOS_ENTREGUES, FINALIZADO, CANCELADO, NO_SHOW
│   ├── Tarefa.java                   # Entidade JPA (extends BaseEntity, @ManyToOne Agendamento + User)
│   ├── StatusTarefa.java             # Enum: PENDENTE, EM_ANDAMENTO, CONCLUIDA, ATRASADA
│   └── TipoTarefa.java               # Enum: EDITAR_FOTOS, ENVIAR_PARA_SELECAO, ENTREGA_FINAL
├── repository/
│   ├── AgendamentoRepository.java    # JpaRepository + JpaSpecificationExecutor + queries customizadas
│   └── TarefaRepository.java         # JpaRepository
├── service/
│   ├── AgendamentoService.java       # ~512 linhas: criação, listagem, atualização, status, reagendar, conflitos, pagamento final
│   ├── TarefaService.java            # ~120 linhas: CRUD de tarefas (só deleta PENDENTE)
│   └── CriarAgendamentoCommand.java  # Record com 20+ campos (inclui MultipartFile)
├── api/
│   ├── AgendamentoController.java     # REST: POST (multipart), GET, PUT, PATCH /status, PATCH /reagendar, PATCH /destaque, POST /pagamento-final, GET /verificar-disponibilidade
│   ├── TarefaController.java          # REST: CRUD + PATCH /status
│   ├── AgendamentoResponse.java       # Record com 30+ campos, factory static of(Agendamento)
│   ├── TarefaResponse.java            # Record com 8 campos, factory static of(Tarefa)
│   ├── AtualizarAgendamentoRequest.java  # Record com @Valid (pacoteId, dataHoraEnsaio, local, etc)
│   ├── AtualizarTarefaRequest.java       # Record com @Valid (tipo, responsavelId, dataLimite)
│   └── DisponibilidadeResponse.java      # Record: disponivel boolean + lista de Conflito
├── event/
│   ├── AgendamentoCriadoEvent.java        # Publicado ao criar
│   ├── AgendamentoConfirmadoEvent.java    # Publicado ao criar + ao reagendar
│   ├── AgendamentoRealizadoEvent.java     # Publicado ao marcar como REALIZADO
│   ├── AgendamentoCanceladoEvent.java     # Publicado ao cancelar/noShow
│   └── PagamentoFinalRegistradoEvent.java # Publicado ao registrar pagamento final
└── exception/
    ├── AgendamentoNaoEncontradoException.java
    ├── AgendamentoNoPassadoException.java
    ├── ConflitoDeAgendaException.java
    ├── EditorNaoEncontradoException.java
    ├── EnsaioNaoFinalizadoException.java
    ├── TarefaNaoEncontradaException.java
    └── TarefaNaoPodeSerExcluidaException.java
```

## 3. Dependências Externas

### Módulos internos (importados via Spring Modulith)
- **auth** → `User` (editor/responsavel), `UserRepository`
- **cliente** → `Cliente`, `OrigemCliente`, `ClienteRepository`, `ClienteNaoEncontradoException`
- **pacote** → `Pacote`, `PacoteRepository`, `PacoteNaoEncontradoException`, `PacoteInativoException`
- **config** → `ConfiguracaoService` (percentualEntrada, taxaDeslocamentoPadrao)
- **foto** → `StatusFoto`, `FotoEnsaioRepository` (usado em `listarAgendamentosCliente`)
- **shared** → `BaseEntity`, `FileStorageService`, `ApplicationEventPublisher`

### Eventos publicados (consumidores em outros módulos)
| Evento | Consumidores |
|--------|-------------|
| AgendamentoCriadoEvent | IndicacaoListener, DocumentoEventListener |
| AgendamentoConfirmadoEvent | AlertaPrazoListener, LembreteEnsaioListener, DocumentoEventListener |
| AgendamentoRealizadoEvent | FinanceiroEventListener |
| AgendamentoCanceladoEvent | IndicacaoListener |
| PagamentoFinalRegistradoEvent | EdicaoListener, IndicacaoListener |

## 4. Fluxos Principais

### Fluxo 1: Criação de Agendamento
1. `POST /api/v1/agendamentos` (multipart/form-data) → `AgendamentoController.criar()` parseia ~20 `@RequestParam` + `MultipartFile`.
2. Valida comprovante (obrigatório, apenas PDF/JPG/PNG).
3. `AgendamentoService.criarAgendamento(CriarAgendamentoCommand)`:
   - **Resolve cliente**: se `clienteId` → busca existente; senão busca por telefone; senão cria novo `Cliente`.
   - Valida pacote ativo, editor, data no passado.
   - Calcula valores: `percentualEntrada` de `ConfiguracaoService`, `valorTotal = pacote.valorBase + taxa`, `valorEntradaExigido = pacote.valorBase * (percentualEntrada/100)`.
   - **Valida conflito de agenda**: se pacote bloqueia dia inteiro → qualquer agendamento no dia; senão → sobreposição de horário no mesmo local.
   - Salva comprovante via `FileStorageService`.
   - Cria `Agendamento` com `status = CONFIRMADO`, `tokenGaleria = UUID.randomUUID()`, `tokenExpiracao = now + 15 dias`.
   - Publica `AgendamentoCriadoEvent` + `AgendamentoConfirmadoEvent`.

### Fluxo 2: Ciclo de Vida (Status Machine)
```
CONFIRMADO ──realizar──▶ AGUARDANDO_PAGAMENTO_FINAL ──pagarFinal──▶ EM_EDICAO
    │                        │                                           │
    ├─reagendar─┐            │                              enviarSelecao│
    └─cancelar──┼─▶ CANCELADO│                                           ▼
                │            │                               FOTOS_ENVIADAS_PARA_SELECAO
                └──▶ NO_SHOW │                                           │
                          (via controller, mesmo PATCH)     confirmarEntrega│
                                                                            ▼
                                                                  FOTOS_ENTREGUES
                                                                            │
                                                                    finalizar│
                                                                            ▼
                                                                     FINALIZADO
```
- `atualizarStatus(id, novoStatus)`: seta data conforme status (`REALIZADO` → `dataRealizacao = now`) e publica eventos.
- `registrarPagamentoFinal(id, comprovante)`: valida status (só REALIZADO ou AGUARDANDO_PAGAMENTO_FINAL), salva comprovante, zera saldo, muda para `EM_EDICAO`, publica `PagamentoFinalRegistradoEvent`.

### Fluxo 3: Gerenciamento de Tarefas
- Criada via `POST /api/v1/tarefas` (recebe `Map<String, Object>`, parsing manual) ou pelo frontend ao avançar de `EM_EDICAO` para `FOTOS_ENVIADAS_PARA_SELECAO`.
- Regras: apenas tarefas `PENDENTE` podem ser excluídas. Ao concluir, `dataConclusao = now` é setada automaticamente.
- Listagem com filtro opcional por `agendamentoId` — se omitido, retorna todas.

## 5. Regras Específicas
1. **Controller usa @RequestParam (não @RequestBody)**: O método `criar()` recebe ~20 parâmetros individuais + MultipartFile por causa de `consumes = MULTIPART_FORM_DATA`. Parsing manual de UUID, BigDecimal, LocalDateTime.
2. **Resolução de cliente em 3 níveis**: `clienteId` > `telefone` (busca existente por telefone) > cria novo `Cliente`. O service pode criar cliente como efeito colateral.
3. **Cálculo financeiro duplicado**: `criarAgendamento()` e `atualizar()` recalculam valorTotal, valorEntradaExigido, valorRestante, valorTotalFinal — mesma lógica em dois lugares.
4. **Conflito de agenda em 2 camadas**: Se `pacote.bloqueiaDiaInteiro == true` → qualquer agendamento não-cancelado no dia. Senão → sobreposição de horário no **mesmo local**.
5. **Eventos como contratos**: O módulo nunca chama services de outros módulos diretamente. Toda comunicação cross-module é via `ApplicationEventPublisher.publishEvent()`.
6. **tokenGaleria**: UUID aleatório com expiração de 15 dias. Usado pelo e-commerce para acesso público à galeria de fotos.
7. **Upload sempre obrigatório**: Tanto na criação (comprovante de entrada) quanto no pagamento final. Validado em 3 pontos: controller (null/empty + contentType), service (null/empty).

## 6. Testes
Não existem testes específicos para este módulo. Apenas `CrmApplicationTests` (smoke test de contexto Spring).

## 7. Pontos de Atenção
- **Controller com ~20 @RequestParam**: Extremamente verboso e frágil. Qualquer novo campo exige alteração em 4 lugares: comando, controller (parse + chamada), service e entidade.
- **`AtualizarTarefaRequest` aceita `TipoTarefa` como enum**, mas `TarefaController.criar()` recebe `Map<String, Object>` e faz parsing manual de string. Inconsistência entre criar (manual) e atualizar (DTO validado).
- **`saldoDevedor` calculado no Response**: `valorTotalFinal - valorEntradaPago` — pode ficar negativo se pagamento a maior.
- **`findByLocalAndDataBetweenExcludingId`**: O parâmetro `local` no nome do método é enganoso — a query JPQL não usa `local`. **[REVISAR HUMANO]**
- **`validarConflitoAgenda` sobrecarga duplicada**: Dois métodos (com e sem `excluirId`) com implementação completa e duplicada.
- **`listarAgendamentosCliente` importa `FotoEnsaioRepository`** do módulo `foto`. Isso viola o isolamento do Modulith — o ideal seria um evento ou consulta no módulo `foto`. **[REVISAR HUMANO]**
- **`@Transactional` em nível de classe**: Todos os métodos do service são transacionais. Métodos `readOnly` sobrescrevem com `@Transactional(readOnly = true)`.
