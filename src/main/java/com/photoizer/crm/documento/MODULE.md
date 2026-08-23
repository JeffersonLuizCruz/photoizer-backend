# Modulo: Documento

## 1. Responsabilidade
Geracao de **contratos e recibos em PDF** para agendamentos e servir **comprovantes de pagamento** (entrada/final). Reage ao `AgendamentoConfirmadoEvent` para gerar o contrato automaticamente.

## 2. Estrutura
```
documento/
├── api/
│   └── DocumentoController.java     # GET /contratos/{id}, /recibos/{id}, /comprovantes/{id}/{tipo}
├── exception/
│   └── TipoComprovanteInvalidoException.java  # Excecao de dominio para tipo invalido
├── listener/
│   └── DocumentoEventListener.java  # Consome AgendamentoConfirmadoEvent → gerarContrato
├── model/
│   ├── TipoComprovante.java         # Enum: ENTRADA, FINAL — substitui magic strings
│   └── TipoDocumento.java           # Enum: CONTRATO, RECIBO — chave de resolucao de estrategias
└── service/
    ├── ContratoPdfStrategy.java     # Estrategia de conteudo para contratos
    ├── DocumentoService.java        # Orquestracao: gerarDocumento/gerarRecibo/resolverComprovante
    ├── PdfContentHelper.java        # Utilitario de formatacao (DRY)
    ├── PdfContentStrategy.java      # Interface generica para estrategias de PDF
    └── ReciboPdfStrategy.java       # Estrategia de conteudo para recibos
```

## 3. Dependencias Externas

### Modulos internos importados
| Modulo | Onde | Uso |
|--------|------|-----|
| **agenda** | `DocumentoService`, `DocumentoController` | `AgendamentoRepository` (leitura), `AgendamentoNaoEncontradoException` |
| **agenda (evento)** | `DocumentoEventListener` | `AgendamentoConfirmadoEvent` (consumo), `ContratoGeradoEvent` (publicacao) |
| **shared** | `DocumentoService` | `PdfWriter` (geracao de PDF) |

### Modulos que dependem deste
Nenhum.

### Eventos consumidos
| Evento (agenda) | Acao |
|------------------|------|
| `AgendamentoConfirmadoEvent` | `DocumentoEventListener.handleAgendamentoConfirmado` → `DocumentoService.gerarContrato` |

### Eventos publicados
| Evento | Consumidor |
|--------|-----------|
| `ContratoGeradoEvent` | `agenda:ContratoGeradoEventListener` → `AgendamentoService.marcarContratoGerado()` |

## 4. Fluxos Principais

### Fluxo 1: Geracao automatica de contrato
1. `agenda` publica `AgendamentoConfirmadoEvent` ao confirmar o agendamento.
2. `DocumentoEventListener.handleAgendamentoConfirmado` chama `DocumentoService.gerarContrato`.
3. `DocumentoService.gerarContrato`: busca agendamento (com `AgendamentoNaoEncontradoException`), monta linhas via `ContratoPdfStrategy`, delega para `PdfWriter.gerar()`, publica `ContratoGeradoEvent`.
4. `agenda:ContratoGeradoEventListener` consome o evento e chama `AgendamentoService.marcarContratoGerado()` → seta `contratoGerado = true`.

### Fluxo 2: Downloads
- `GET /api/v1/documentos/contratos/{agendamentoId}` → `gerarContrato` (PDF via `PdfWriter`).
- `GET /api/v1/documentos/recibos/{agendamentoId}` → `gerarRecibo` (PDF via `PdfWriter`).
- `GET /api/v1/documentos/comprovantes/{agendamentoId}/{tipo}` → serve o comprovante (`entrada`/`final`) do filesystem via `FileServeHelper` com content-type resolvido.

## 5. Patterns Aplicados

| Pattern | Classe | Motivo |
|---------|--------|--------|
| **Strategy Pattern (Generic)** | `PdfContentStrategy<T>`, `ContratoPdfStrategy`, `ReciboPdfStrategy` | Isola logica de formatacao por tipo de PDF. Generics eliminam casting manual (type-safety). Novos tipos adicionados sem modificar DocumentoService (Open/Closed). |
| **Facade** | `shared/pdf/PdfWriter` | Interface simples para geracao de PDF, esconde complexidade de bytes PDF nativos. |
| **Facade (Download)** | `DocumentoService.resolverComprovante()` | Centraliza logica de resolucao de comprovante, eliminando acoplamento do controller com `AgendamentoRepository`. |
| **Enum Type Safety** | `TipoComprovante`, `TipoDocumento` | Substitui magic strings por tipos seguros com compile-time checking. |
| **Event-Driven Decoupling** | `DocumentoEventListener` + `ContratoGeradoEvent` | Desacopla geracao de PDF da transacao do evento original. Flag `contratoGerado` fica sob dominio do modulo `agenda`. |
| **DRY** | `PdfContentHelper` | Elimina duplicacao de `formatarValor()` entre strategies. |
| **Role-Based Access Control** | `@RolesAllowed` no controller | Garante que apenas ADMIN e FOTOGRAFO acessem endpoints de download. |

## 6. Testes
Nenhum teste especifico. Apenas `CrmApplicationTests` (smoke de contexto).

## 7. Dividas Resolvidas (refatoracao atual)

| Divida | Status | Solucao |
|--------|--------|---------|
| PDF stub (`byte[0]`) | **RESOLVIDO** | Delega para `PdfWriter` no shared |
| Escrita cross-module (`contratoGerado`) | **RESOLVIDO** | Evento `ContratoGeradoEvent` → `AgendamentoService.marcarContratoGerado()` |
| `FileStorageService` injetado sem uso | **RESOLVIDO** | Removido do controller |
| Magic strings `"entrada"`/`"final"` | **RESOLVIDO** | Enum `TipoComprovante` com factory method `fromValor()` |
| Extensao `.jpg` cravada | **RESOLVIDO** | Resolucao via `Files.probeContentType()` + extensao do arquivo |
| `orElseThrow()` sem mensagem | **RESOLVIDO** | `AgendamentoNaoEncontradoException` |
| Conflito de nome (`ContratoService`) | **RESOLVIDO** | Renomeado para `DocumentoService` |
| Listener sem tratamento de erro | **RESOLVIDO** | `try/catch` com log no `DocumentoEventListener` |
| **Seguranca: endpoints sem controle de acesso** | **RESOLVIDO** | `@RolesAllowed({"ADMIN", "FOTOGRAFO"})` no controller |
| **Acoplamento: controller injetava AgendamentoRepository** | **RESOLVIDO** | Logica movida para `DocumentoService.resolverComprovante()` via Facade |
| **Duplicacao: formatarValor() em 2 strategies** | **RESOLVIDO** | Extraido para `PdfContentHelper.formatarValor()` (DRY) |
| **Type-safety: PdfContentStrategy<Object>** | **RESOLVIDO** | Generics `PdfContentStrategy<T>` elimina casting manual |
| **String keys: resolucao por string propensa a erros** | **RESOLVIDO** | Enum `TipoDocumento` como chave de resolucao |
| **Excecao generica: IllegalArgumentException** | **RESOLVIDO** | `TipoComprovanteInvalidoException` mapeada pelo `GlobalExceptionHandler` (400) |

## 8. Dividas Remanescentes

### 8.1 PDF nativo sem biblioteca — **P1** (pendente)
- `PdfWriter` gera bytes PDF via OpenPDF (herdado do modulo `contrato`). Frágil, sem suporte a layout complexo.
- **Solucao futura**: migrar para PDFBox/Flying Saucer (templates HTML).

### 8.2 Duplicacao de comprovante com modulo contrato — **P2** (pendente)
- `downloadComprovante` pode duplicar logica do `ContratoController`.
- **Solucao futura**: centralizar em `shared/storage`.

## 9. Arquivos afetados (refatoracao)
- `documento/service/DocumentoService.java` — adicionar `resolverComprovante()`, `TipoDocumento` key, generics
- `documento/service/PdfContentStrategy.java` — generico `PdfContentStrategy<T>`, retorna `TipoDocumento`
- `documento/service/ContratoPdfStrategy.java` — implements `PdfContentStrategy<Agendamento>`, usa `PdfContentHelper`
- `documento/service/ReciboPdfStrategy.java` — implements `PdfContentStrategy<Agendamento>`, usa `PdfContentHelper`
- `documento/service/PdfContentHelper.java` — novo (DRY)
- `documento/model/TipoDocumento.java` — novo enum
- `documento/exception/TipoComprovanteInvalidoException.java` — novo
- `documento/api/DocumentoController.java` — removido `AgendamentoRepository`, `@RolesAllowed`
- `documento/model/TipoComprovante.java` — usa `TipoComprovanteInvalidoException`
- `shared/exception/GlobalExceptionHandler.java` — handler para `TipoComprovanteInvalidoException`
