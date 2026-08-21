# Módulo: Comissão

## 1. Responsabilidade
Gerencia comissões de indicação. É um módulo **puramente reativo** — não expõe endpoints de criação (as comissões nascem do `AgendamentoCriadoEvent` do módulo `agenda` ou do `ComissaoSolicitadaEvent` do módulo `financeiro`). Fornece consultas públicas (por telefone) e agregado de indicadores.

## 2. Estrutura
```
comissao/
├── model/
│   ├── Indicacao.java              # Entidade (extends BaseEntity): agendamentoId/indicadorId (UUID soltos), nome/tel do indicador, origem enum, percentual, valorReferencia, valorComissao, status enum, dataPagamento
│   ├── StatusIndicacao.java        # Enum: PENDENTE, PAGA, CANCELADA (com métodos de validação de transição)
│   └── OrigemIndicacao.java        # Enum: PACOTE, INDICADOR, FOTO_EXTRA, VIDEO_EXTRA
├── repository/
│   ├── IndicacaoRepository.java    # JpaRepository + findBy* + query agregada findIndicadoresComResumo (GROUP BY)
│   └── projection/
│       └── IndicadorComissaoProjection.java  # Interface de projeção leve para resultados agregados
├── service/
│   ├── IndicacaoService.java       # criar (usa ComissaoCalculator), marcarTodasComoPaga/Cancelada, consultarPorTelefone, consultarComAgendamento, listarResumoIndicadores (facade)
│   ├── ComissaoCalculator.java     # Componente de cálculo centralizado (elimina duplicação com financeiro)
│   └── IndicacaoListener.java      # @EventListener: reage a 4 eventos (AgendamentoCriado, PagamentoFinal, AgendamentoCancelado, ComissaoSolicitada)
├── event/
│   └── ComissaoSolicitadaEvent.java  # Evento de domínio para escrita cross-module (financeiro → comissao)
└── api/
    ├── IndicacaoController.java    # GET /consulta?telefone=, GET /indicadores — controller slim (1 dependência)
    ├── IndicacaoResponse.java      # Record c/ dados da indicação + dados do agendamento (usa enums)
    ├── ConsultaComissoesResponse.java  # Record tipado para resposta de consulta (substitui Map<String,Object>)
    └── IndicadorResumoResponse.java    # Record tipado para listagem de indicadores (substitui Map<String,Object>)
```

## 3. Dependências Externas

### Módulos internos importados — **[VIOLAÇÕES Modulith]**
| Módulo | Onde | Uso |
|--------|------|-----|
| **agenda** | `IndicacaoListener` | eventos `AgendamentoCriadoEvent`, `PagamentoFinalRegistradoEvent`, `AgendamentoCanceladoEvent` (uso correto de eventos) |
| **agenda** | `IndicacaoService` | `AgendamentoRepository` para enriquecer consulta (leitura — violação menor) |
| **indicador** | `IndicacaoListener` | `IndicadorService.buscarPorId/buscarOuCriar` |
| **indicador** | `IndicacaoService` | `IndicadorRepository.findByTelefone` e `findAll` para listagem |
| **config** | `IndicacaoListener` | `ConfiguracaoService.getValorDecimal("percentualComissao", 10)` |
| **shared** | model | `BaseEntity` (herança) |

### Módulos que dependem deste (importam `comissao.*`) — **[VIOLAÇÕES REDUZIDAS]**
| Módulo | Uso |
|--------|-----|
| **financeiro** | `FinanceiroService` importa `Indicacao` (apenas leitura para resumos financeiros); **usa `ComissaoSolicitadaEvent` para criação** (violação de escrita resolvida) |
| **dashboard** | `DashboardService` lê `IndicacaoRepository.findByAgendamentoIdIn` |
| **agenda** | `AgendamentoController` lê `IndicacaoRepository.findAllByAgendamentoId` |
| **indicador** | `IndicadorController` lê `IndicacaoRepository.findByIndicadorId` |
| **shared** | `GlobalExceptionHandler` (tratamento removido — código morto eliminado) |

### Eventos consumidos
| Evento | Ação |
|--------|------|
| `AgendamentoCriadoEvent` | Se `indicadorNome` e `indicadorTelefone` preenchidos → cria `Indicacao` (origem `INDICADOR` se `indicadorId` presente, senão `PACOTE`) |
| `PagamentoFinalRegistradoEvent` | Marca todas `Indicacao` PENDENTE do agendamento como PAGA (`pagar()` → `dataPagamento=now`) |
| `AgendamentoCanceladoEvent` | Marca todas `Indicacao` PENDENTE como CANCELADA (`cancelar()`) |
| `ComissaoSolicitadaEvent` | Financeiro solicita criação de comissão para fotos/vídeos extras — processado pelo listener que busca/cria indicador e delega para `IndicacaoService.criar()` |

### Eventos publicados
Nenhum.

## 4. Fluxos Principais

### Fluxo 1: Criação de Comissão (via evento de agenda)
1. `agenda` publica `AgendamentoCriadoEvent` ao criar agendamento com indicação.
2. `IndicacaoListener.handleAgendamentoCriado` (`IndicacaoListener.java:40-72`):
   - Ignora se `indicadorNome`/`indicadorTelefone` em branco.
   - `indicador = indicadorId != null ? indicadorService.buscarPorId : indicadorService.buscarOuCriar(...)`.
   - `percentual = indicador.getPercentualComissao() ?? config("percentualComissao", 10)`.
   - **Ignora `event.percentualComissao()` do evento** (campo existe no evento, ver seção 5.5).
   - `IndicacaoService.criar(...)` com `status StatusIndicacao.PENDENTE`; `valorComissao` calculado por `ComissaoCalculator`.

### Fluxo 2: Criação de Comissão (via evento de domínio — financeiro)
1. `financeiro` publica `ComissaoSolicitadaEvent` ao adicionar foto/vídeo extra com indicação.
2. `IndicacaoListener.handleComissaoSolicitada` (`IndicacaoListener.java:74-106`):
   - Valida se há dados do indicador.
   - Busca ou cria indicador via `IndicadorService`.
   - Calcula percentual (indicador ou config global).
   - Delega para `IndicacaoService.criar()`.

### Fluxo 3: Pagamento/Cancelamento de Comissões
- `PagamentoFinalRegistradoEvent` → `handlePagamentoRegistrado` → `marcarTodasComoPaga` (usa `Indicacao.pagar()`).
- `AgendamentoCanceladoEvent` → `handleAgendamentoCancelado` → `marcarTodasComoCancelada` (usa `Indicacao.cancelar()`).

### Fluxo 4: Consulta por Telefone
`GET /api/v1/comissoes/consulta?telefone=` (`IndicacaoController.java:38-41`):
1. Delega para `IndicacaoService.consultarComAgendamento()`.
2. Service busca indicações, enriquece com dados do agendamento, calcula totais.
3. Retorna `ConsultaComissoesResponse` (record tipado).

### Fluxo 5: Agregado de Indicadores
`GET /api/v1/comissoes/indicadores` (`IndicacaoController.java:44-47`):
1. Delega para `IndicacaoService.listarResumoIndicadores()`.
2. Service usa query agregada `findIndicadoresComResumo()` (GROUP BY — sem N+1).
3. Complementa com indicadores cadastrados sem comissão.
4. Retorna `List<IndicadorResumoResponse>` (record tipado).

## 5. Regras Específicas
1. **Módulo reativo**: sem endpoint de criação manual; toda comissão nasce de eventos.
2. **`StatusIndicacao` enum**: `PENDENTE`, `PAGA`, `CANCELADA` — transições validadas por métodos de domínio `pagar()` e `cancelar()`.
3. **`OrigemIndicacao` enum**: `PACOTE`, `INDICADOR`, `FOTO_EXTRA`, `VIDEO_EXTRA` — valores controlados, sem strings soltas.
4. **`agendamentoId`/`indicadorId` são UUIDs soltos** (sem `@ManyToOne`/FK — por design para desacoplar).
5. **Divergência de percentual**: `AgendamentoCriadoEvent` carrega `percentualComissao` mas o listener o ignora — o valor efetivo vem do `Indicador` ou da config `percentualComissao`.
6. **Consulta pública expõe dados financeiros** (`valorTotalFinal`, `valorExtras` do agendamento) na resposta de telefone.
7. **`ComissaoCalculator`** centraliza o cálculo `valorReferencia × percentual / 100` (elimina duplicação com financeiro).

## 6. Testes
Nenhum teste específico. Apenas `CrmApplicationTests` (smoke de contexto).

## 7. Dívidas Técnicas Resolvidas Nesta Refatoração

| Dívida | Status | Solução |
|--------|--------|---------|
| Controller dependente de 4 módulos + `Map<String,Object>` | **RESOLVIDO** | Controller slim com 1 dependência; facade `IndicacaoService` absorve orquestração |
| N+1 queries em `listarIndicadores` | **RESOLVIDO** | Query agregada `findIndicadoresComResumo` com `GROUP BY` |
| Escrita cross-module do financeiro | **RESOLVIDO** | `ComissaoSolicitadaEvent` — financeiro publica, comissao consome |
| `status`/`origem` como String | **RESOLVIDO** | Enums `StatusIndicacao` e `OrigemIndicacao` com `@Enumerated(STRING)` |
| Código morto `IndicacaoNaoEncontradaException` | **RESOLVIDO** | Classe removida + handler removido do `GlobalExceptionHandler` |
| Duplicação do cálculo de comissão | **RESOLVIDO** | `ComissaoCalculator` centraliza a regra |
| `Map<String,Object>` em vez de DTOs tipados | **RESOLVIDO** | Records `ConsultaComissoesResponse` e `IndicadorResumoResponse` |
| Literais String duplicados | **RESOLVIDO** | Enums eliminam comparações `String.equals` |
| Métodos de transição de estado ausentes | **RESOLVIDO** | `Indicacao.pagar()` e `cancelar()` com validação |

## 8. Dívidas Restantes

| Dívida | Prioridade | Nota |
|--------|------------|------|
| Leitura cross-module (financeiro/dashboard/agenda leem `IndicacaoRepository`) | P2 | Menor que escrita; pode ser resolvido com facade pública no futuro |
| `BaseEntity` inheritance pattern | P | Padrão transversal do projeto |
| Sem testes unitários/integração | P1 | Necessário cobertura mínima para regras de negócio |

## 9. Design Patterns Aplicados

| Pattern | Onde | Motivo |
|---------|------|--------|
| **State Pattern** (simplificado) | `StatusIndicacao.podePagar()/podeCancelar()` + `Indicacao.pagar()/cancelar()` | Encapsula regras de transição de estado; previne transições inválidas em compile-time |
| **Strategy** | `ComissaoCalculator` | Extração da regra de cálculo duplicada; facilita mudanças futuras e testes isolados |
| **Facade** | `IndicacaoService` como facade interna | Encapsula orquestração (repository + enriquecimento + cálculos); controller não conhece módulos externos |
| **Domain Event** | `ComissaoSolicitadaEvent` | Elimina escrita cross-module; mantém desacoplamento Modulith |
| **Projection** (Interface) | `IndicadorComissaoProjection` | Projeção leve para queries agregadas sem carregar entidade completa |
