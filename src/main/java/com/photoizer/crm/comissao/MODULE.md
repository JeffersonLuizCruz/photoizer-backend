# Módulo: Comissão

## 1. Responsabilidade
Gerencia comissões de indicação. É um módulo **puramente reativo** — não expõe endpoints de criação (as comissões nascem do `AgendamentoCriadoEvent` do módulo `agenda`). Fornece consultas públicas (por telefone) e agregado de indicadores, e é **gravado diretamente** pelo módulo `financeiro` (comissões de extras).

## 2. Estrutura
```
comissao/
├── model/
│   └── Indicacao.java          # Entidade (extends BaseEntity): agendamentoId/indicadorId (UUID soltos), nome/tel do indicador, origem String, percentual, valorReferencia, valorComissao, status String, dataPagamento
├── repository/
│   └── IndicacaoRepository.java # JpaRepository + findBy* (agendamentoId, indicadorId, telefone), findByAgendamentoIdIn, findAllDistinctTelefones
├── service/
│   ├── IndicacaoService.java    # criar (calcula comissão), marcarTodasComoPaga, marcarTodasComoCancelada, consultarPorTelefone
│   └── IndicacaoListener.java   # @EventListener: reage a 3 eventos de agenda
├── api/
│   ├── IndicacaoController.java # GET /consulta?...telefone=, GET /indicadores — retorna Map<String,Object>
│   └── IndicacaoResponse.java   # Record c/ dados da indicação + dados financeiros do agendamento
└── exception/
    └── IndicacaoNaoEncontradaException.java # RuntimeException — mapeada no GlobalExceptionHandler, mas NUNCA lançada (código morto)
```

## 3. Dependências Externas

### Módulos internos importados — **[VIOLAÇÕES Modulith]**
| Módulo | Onde | Uso |
|--------|------|-----|
| **agenda** | `IndicacaoListener` | eventos `AgendamentoCriadoEvent`, `PagamentoFinalRegistradoEvent`, `AgendamentoCanceladoEvent` (uso correto de eventos) |
| **agenda** | `IndicacaoController` | injeta `AgendamentoRepository` + `Agendamento` para enriquecer a consulta |
| **indicador** | `IndicacaoListener` | `IndicadorService.buscarPorId/buscarOuCriar` |
| **indicador** | `IndicacaoController` | injeta `IndicadorService` + `IndicadorRepository` — **controller chamando service de outro módulo** |
| **config** | `IndicacaoListener` | `ConfiguracaoService.getValorDecimal("percentualComissao", 10)` |
| **shared** | model | `BaseEntity` (herança) |

### Módulos que dependem deste (importam `comissao.*`) — **[VIOLAÇÕES]**
| Módulo | Uso |
|--------|-----|
| **financeiro** | `FinanceiroService`/`FinanceiroDashboardService` importam `Indicacao`/`IndicacaoRepository`; **FinanceiroService CRIA `Indicacao` diretamente** (`criarComissaoSeNecessario`) |
| **dashboard** | `DashboardService` lê `IndicacaoRepository.findByAgendamentoIdIn` |
| **agenda** | `AgendamentoController` lê `IndicacaoRepository.findAllByAgendamentoId` (endpoint de indicações) |
| **indicador** | `IndicadorController` lê `IndicacaoRepository.findByIndicadorId` |
| **shared** | `GlobalExceptionHandler` mapeia `IndicacaoNaoEncontradaException` |

O módulo **depende de indicações e é dependido por 5 módulos** — não é terminal como o doc antigo afirmava.

### Eventos consumidos
| Evento (agenda) | Ação |
|------------------|------|
| `AgendamentoCriadoEvent` | Se `indicadorNome` e `indicadorTelefone` preenchidos → cria `Indicacao` (origem `INDICADOR` se `indicadorId` presente, senão `PACOTE`) |
| `PagamentoFinalRegistradoEvent` | Marca todas `Indicacao` PENDENTE do agendamento como PAGA (`dataPagamento=now`) |
| `AgendamentoCanceladoEvent` | Marca todas `Indicacao` PENDENTE como CANCELADA |

### Eventos publicados
Nenhum.

## 4. Fluxos Principais

### Fluxo 1: Criação de Comissão (via evento)
1. `agenda` publica `AgendamentoCriadoEvent` ao criar agendamento com indicação.
2. `IndicacaoListener.handleAgendamentoCriado` (`IndicacaoListener.java:35-61`):
   - Ignora se `indicadorNome`/`indicadorTelefone` em branco.
   - `indicador = indicadorId != null ? indicadorService.buscarPorId : indicadorService.buscarOuCriar(...)`.
   - `percentual = indicador.getPercentualComissao() ?? config("percentualComissao", 10)`.
   - **Ignora `event.percentualComissao()` do evento** (campo existe no evento, ver seção 5.5).
   - `IndicacaoService.criar(...)` com `status "PENDENTE"`; `valorComissao = valorReferencia × percentual / 100` (HALF_UP, 2 casas) (`IndicacaoService.java:26`).

### Fluxo 2: Pagamento/Cancelamento de Comissões
- `PagamentoFinalRegistradoEvent` → `handlePagamentoRegistrado` (`IndicacaoListener.java:65-68`) → `marcarTodasComoPaga` (`IndicacaoService.java:43-52`).
- `AgendamentoCanceladoEvent` → `handleAgendamentoCancelado` (`:72-75`) → `marcarTodasComoCancelada` (`:54-62`).

### Fluxo 3: Consulta por Telefone
`GET /api/v1/comissoes/consulta?telefone=` (`IndicacaoController.java:49-92`):
1. Busca indicações por telefone; carrega agendamentos via `agendamentoRepository.findAllById` e monta `IndicacaoResponse.of` com `cliente/pacote/valorTotalFinal/valorExtras/dataHoraEnsaio`.
2. Soma `totalPendente`/`totalPago`; retorna `Map<String,Object>`.

### Fluxo 4: Agregado de Indicadores
`GET /api/v1/comissoes/indicadores` (`IndicacaoController.java:94-157`):
1. Para cada telefone distinto (`findAllDistinctTelefones`), **refaz** `findByIndicadorTelefoneOrderByCreatedAtDesc` e soma PENDENTE/PAGA/CANCELADA.
2. Adiciona indicadores cadastrados sem comissão (`indicadorRepository.findAll()`).
3. Retorna `List<Map<String,Object>>` construído com `HashMap`.

## 5. Regras Específicas
1. **Módulo reativo**: sem endpoint de criação manual; toda comissão nasce de evento de agenda.
2. **`status` String (não enum)**: `"PENDENTE"`, `"PAGA"`, `"CANCELADA"` comparados com `String.equals` — qualquer string inválida é aceita e nunca transiciona.
3. **`origem` String**: `"PACOTE"` ou `"INDICADOR"` (hardcoded no listener); `FinanceiroService` usa `"PACOTE"` também — não há enum.
4. **`agendamentoId`/`indicadorId` são UUIDs soltos** (sem `@ManyToOne`/FK — por design para desacoplar), o que força `IndicacaoController` a buscar `Agendamento` na mão para enriquecer.
5. **Divergência de percentual**: `AgendamentoCriadoEvent` carrega `percentualComissao` mas o listener o ignora — o valor efetivo vem do `Indicador` ou da config `percentualComissao`. Decisão de negócio a confirmar (evento pode estar desatualizado).
6. **Consulta pública expõe dados financeiros** (`valorTotalFinal`, `valorExtras` do agendamento) na resposta de telefone.
7. **`IndicacaoResponse.of`** exige agendamento existente — se o agendamento faltar, o item é silenciosamente descartado (`IndicacaoController.java:63`).

## 6. Testes
Nenhum teste específico. Apenas `CrmApplicationTests` (smoke de contexto).

## 7. Dívidas Técnicas e Melhorias Recomendadas

### 7.1 Controller dependente de 4 módulos + agregação manual — **[CRÍTICO] P1
- `IndicacaoController` injeta `IndicacaoRepository`, `AgendamentoRepository`, `IndicadorService`, `IndicadorRepository` e constrói `Map<String,Object>` com loops de soma.
- **Solução**: mover agregações para `IndicacaoService`/repositório (`SUM`/`GROUP BY`); o módulo `agenda` deve expor DTO próprio; `IndicadorResponse` deve ser um record dedicado, não `Map`.

### 7.2 Queries N+1 — **P1**
- `listarIndicadores` faz `findByIndicadorTelefone...` para **cada** telefone distinto (`IndicacaoController.java:103`) + `indicadorRepository.findByTelefone` por telefone (:123) + `findAll()` (:140).
- **Solução**: query agregada única com agrupamento por telefone (`SELECT telefone, status, SUM(valorComissao) ... GROUP BY`) + projeção do percentual do indicador.

### 7.3 Escrita cross-module do financeiro — **P1**
- `FinanceiroService.criarComissaoSeNecessario` cria `Indicacao` diretamente com `status "PENDENTE"` e reusa a **mesma regra de cálculo** do comissao.
- **Solução**: o módulo `comissao` deve ser o único dono da escrita — expor o cálculo em um componente público (`ComissaoCalculator`) ou publicar `ComissaoSolicitadaEvent`; o financeiro envia o evento, o comissao cria.

### 7.4 Código morto — `IndicacaoNaoEncontradaException` reduziu **P2**
- Mapeada em `GlobalExceptionHandler.java:76-77` mas nenhum código lança — serviço usa `.orElseThrow()` inline.
- **Solução**: lançar na `findById` do serviço ou remover a classe e o handler.

### 7.5 `status` em String → enum — **P2** (padrão-aplicável)
- Substituir por `enum StatusIndicacao { PENDENTE, PAGA, CANCELADA }` com transições explícitas via métodos de domínio (`pagar()`, `cancelar()`) — elimina comparações de String espalhadas e aceita apenas valores válidos.

### 7.6 Duplicação da regra de cálculo de comissão — **P2**
- `valorReferencia × percentual / 100` duplicado em `IndicacaoService.criar` e `FinanceiroService` (comissão de extras).
- **Solução**: componente único no `shared` ou no `comissao` (dono do conceito), usado por ambos.

### 7.7 DTOs manuais e resposta inconsistente — **P3**
- `IndicacaoController` retorna `Map<String,Object>`; `IndicacaoResponse` (`static of`) é o único DTO.
- **Solução**: records tipados (`ConsultaIndicacaoResponse`, `ResumoIndicadorResponse`) com MapStruct.

### 7.8 Herança `BaseEntity` — **P** (padrão-aplicável)
- `Indicacao` estende `@MappedSuperclass`.
- **Solução**: `@Embeddable AuditInfo` + Auditing.

### 7.9 Expressões duplicadas de string — **P3**
- Literais `"PENDENTE"`, `"PAGA"`, `"CANCELADA"`, `"PACOTE"`, `"INDICADOR"` repetidos em service, listener e controller.

## 8. Exemplos de arquivos afetados
- `IndicacaoController.java:49-92, 94-157` — controller gordo, 4 injeções, `Map<String,Object>`, N+1.
- `IndicacaoListener.java:35-61` — ignora `event.percentualComissao()`; dependências diretas de indicador/config.
- `IndicacaoService.java:24-67` — cálculo único de comissão (duplicado no financeiro).
- `Indicacao.java:63-65` — `status` em String sem validação.
- `GlobalExceptionHandler.java:76-77` — handler de exceção nunca lançada.
- `FinanceiroService.java:549-586` — criação direta de `Indicacao` (violação cross-module, ver financeiro/MODULE.md 7.3).