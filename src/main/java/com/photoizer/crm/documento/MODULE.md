# Módulo: Documento

## 1. Responsabilidade
Geração de **contratos e recibos em PDF** para agendamentos e servir **comprovantes de pagamento** (entrada/final). Reage ao `AgendamentoConfirmadoEvent` para gerar o contrato automaticamente.

## 2. Estrutura
```
documento/
├── model/
│   └── TipoComprovante.java         # Enum: ENTRADA, FINAL — substitui magic strings
├── service/
│   └── DocumentoService.java        # Orquestração: gerarContrato/gerarRecibo (delega para PdfWriter)
├── api/
│   └── DocumentoController.java     # GET /contratos/{id}, /recibos/{id}, /comprovantes/{id}/{tipo}
└── listener/
    └── DocumentoEventListener.java  # Consome AgendamentoConfirmadoEvent → gerarContrato
```

## 3. Dependências Externas

### Módulos internos importados
| Módulo | Onde | Uso |
|--------|------|-----|
| **agenda** | `DocumentoService`, `DocumentoController` | `AgendamentoRepository` (leitura), `AgendamentoNaoEncontradoException` |
| **agenda (evento)** | `DocumentoEventListener` | `AgendamentoConfirmadoEvent` (consumo), `ContratoGeradoEvent` (publicação) |
| **shared** | `DocumentoService` | `PdfWriter` (geração de PDF) |

### Módulos que dependem deste
Nenhum.

### Eventos consumidos
| Evento (agenda) | Ação |
|------------------|------|
| `AgendamentoConfirmadoEvent` | `DocumentoEventListener.handleAgendamentoConfirmado` → `DocumentoService.gerarContrato` |

### Eventos publicados
| Evento | Consumidor |
|--------|-----------|
| `ContratoGeradoEvent` | `agenda:ContratoGeradoEventListener` → `AgendamentoService.marcarContratoGerado()` |

## 4. Fluxos Principais

### Fluxo 1: Geração automática de contrato
1. `agenda` publica `AgendamentoConfirmadoEvent` ao confirmar o agendamento.
2. `DocumentoEventListener.handleAgendamentoConfirmado` chama `DocumentoService.gerarContrato`.
3. `DocumentoService.gerarContrato`: busca agendamento (com `AgendamentoNaoEncontradoException`), monta linhas, delega para `PdfWriter.gerar()`, publica `ContratoGeradoEvent`.
4. `agenda:ContratoGeradoEventListener` consome o evento e chama `AgendamentoService.marcarContratoGerado()` → seta `contratoGerado = true`.

### Fluxo 2: Downloads
- `GET /api/v1/documentos/contratos/{agendamentoId}` → `gerarContrato` (PDF via `PdfWriter`).
- `GET /api/v1/documentos/recibos/{agendamentoId}` → `gerarRecibo` (PDF via `PdfWriter`).
- `GET /api/v1/documentos/comprovantes/{agendamentoId}/{tipo}` → serve o comprovante (`entrada`/`final`) do filesystem via `FileSystemResource` com content-type resolvido.

## 5. Patterns Aplicados

| Pattern | Classe | Motivo |
|---------|--------|--------|
| **Facade** | `shared/pdf/PdfWriter` | Interface simples para geração de PDF, esconde complexidade de bytes PDF nativos. Elimina duplicação com módulo `contrato`. |
| **Enum Type Safety** | `documento/model/TipoComprovante` | Substitui magic strings por tipo seguro com compile-time checking. |
| **Event-Driven Decoupling** | `DocumentoEventListener` + `ContratoGeradoEvent` | Desacopla geração de PDF da transação do evento original. Flag `contratoGerado` fica sob domínio do módulo `agenda`. |

## 6. Testes
Nenhum teste específico. Apenas `CrmApplicationTests` (smoke de contexto).

## 7. Dívidas Resolvidas (refatoração atual)

| Dívida | Status | Solução |
|--------|--------|---------|
| PDF stub (`byte[0]`) | **RESOLVIDO** | Delega para `PdfWriter` no shared (movido de `contrato`) |
| Escrita cross-module (`contratoGerado`) | **RESOLVIDO** | Evento `ContratoGeradoEvent` → `AgendamentoService.marcarContratoGerado()` |
| `FileStorageService` injetado sem uso | **RESOLVIDO** | Removido do controller |
| Magic strings `"entrada"`/`"final"` | **RESOLVIDO** | Enum `TipoComprovante` com factory method `fromValor()` |
| Extensão `.jpg` cravada | **RESOLVIDO** | Resolução via `Files.probeContentType()` + extensão do arquivo |
| `orElseThrow()` sem mensagem | **RESOLVIDO** | `AgendamentoNaoEncontradoException` |
| Conflito de nome (`ContratoService`) | **RESOLVIDO** | Renomeado para `DocumentoService` |
| Listener sem tratamento de erro | **RESOLVIDO** | `try/catch` com log no `DocumentoEventListener` |

## 8. Dívidas Remanescentes

### 8.1 Segurança — **P1** (pendente)
- `/api/v1/documentos/**` cai em `anyRequest().authenticated()` (não `denyAll`). Endpoints são acessíveis para qualquer autenticado.
- **Solução futura**: definir papéis específicos via `@PreAuthorize` ou regras no `SecurityConfig`.

### 8.2 PDF nativo sem biblioteca — **P1** (pendente)
- `PdfWriter` gera bytes PDF 1.4 manualmente (herdado do módulo `contrato`). Frágil, sem suporte a layout complexo.
- **Solução futura**: migrar para OpenPDF/PDFBox/Flying Saucer.

### 8.3 Duplicação de comprovante com módulo contrato — **P2** (pendente)
- `downloadComprovante` pode duplicar lógica do `ContratoController`.
- **Solução futura**: centralizar em `shared/storage`.

## 9. Arquivos afetados (refatoração)
- `documento/service/DocumentoService.java` — novo (substitui `ContratoService` + `PdfGeneratorService`)
- `documento/model/TipoComprovante.java` — novo enum
- `documento/api/DocumentoController.java` — removido `FileStorageService`, usa enum, content-type resolvido
- `documento/listener/DocumentoEventListener.java` — usa `DocumentoService`, trata erros
- `shared/pdf/PdfWriter.java` — movido de `contrato/service/ContratoPdfWriter.java`
- `agenda/event/ContratoGeradoEvent.java` — novo
- `agenda/service/AgendamentoService.java` — novo método `marcarContratoGerado()`
- `agenda/listener/ContratoGeradoEventListener.java` — novo
- `contrato/service/ContratoPublicoService.java` — import atualizado para `PdfWriter`
