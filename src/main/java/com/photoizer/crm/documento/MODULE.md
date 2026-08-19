# Módulo: Documento

## 1. Responsabilidade
Geração de **contratos e recibos em PDF** para agendamentos e servir **comprovantes de pagamento** (entrada/final). Reage ao `AgendamentoConfirmadoEvent` para gerar o contrato automaticamente.

> **Estado real**: a geração de PDF é um **stub** (`PdfGeneratorService` retorna `byte[0]`), e **nenhum endpoint é alcançável** pela segurança — ver 7.1 e 7.2 (módulo na prática **morto**).

## 2. Estrutura
```
documento/
├── service/
│   ├── ContratoService.java        # 34 linhas: gerarContrato/gerarRecibo (seta contratoGerado=true no Agendamento)
│   └── PdfGeneratorService.java    # 22 linhas: STUB — gerarContrato/gerarRecibo retornam new byte[0]
├── api/
│   └── DocumentoController.java    # GET /contratos/{id}, /recibos/{id}, /comprovantes/{id}/{tipo}
└── listener/
    └── DocumentoEventListener.java # Consome AgendamentoConfirmadoEvent → gerarContrato
```

## 3. Dependências Externas

### Módulos internos importados — **[VIOLAÇÕES Modulith]**
| Módulo | Onde | Uso |
|--------|------|-----|
| **agenda** | `ContratoService`, `DocumentoController` | `Agendamento`, `AgendamentoRepository`, `AgendamentoNaoEncontradoException` |
| **agenda** | `ContratoService.java:25-26` | **escrita cross-module**: `agendamento.setContratoGerado(true)` + `save` |
| **agenda (evento)** | `DocumentoEventListener` | `AgendamentoConfirmadoEvent` (uso correto) |
| **shared** | `DocumentoController` | `FileStorageService` injetado, mas **sem uso** |

### Módulos que dependem deste
Nenhum.

### Eventos consumidos
| Evento (agenda) | Ação |
|------------------|------|
| `AgendamentoConfirmadoEvent` | `DocumentoEventListener.handleAgendamentoConfirmado` → `ContratoService.gerarContrato` (gera PDF **vazio** e marca `contratoGerado=true`) |

### Eventos publicados
Nenhum.

## 4. Fluxos Principais

### Fluxo 1: Geração automática de contrato
1. `agenda` publica `AgendamentoConfirmadoEvent` ao confirmar o agendamento.
2. `DocumentoEventListener.handleAgendamentoConfirmado` (`DocumentoEventListener.java:21-25`) chama `ContratoService.gerarContrato`.
3. `ContratoService.gerarContrato` (`ContratoService.java:23-28`): `orElseThrow()` sem mensagem, seta `contratoGerado = true` no agendamento (outro módulo), salva e retorna `PdfGeneratorService.gerarContrato` → **byte[0]** (PDF vazio).

### Fluxo 2: Downloads
- `GET /api/v1/documentos/contratos/{agendamentoId}` → `gerarContrato` (PDF vazio).
- `GET /api/v1/documentos/recibos/{agendamentoId}` → `gerarRecibo` (PDF vazio).
- `GET /api/v1/documentos/comprovantes/{agendamentoId}/{tipo}` → serve o comprovante (`entrada`/`final`) do filesystem via `FileSystemResource`.

## 5. Regras Específicas
1. **`PdfGeneratorService` é stub**: `gerarContrato`/`gerarRecibo` apenas logam e retornam `new byte[0]` (`PdfGeneratorService.java:13-21`) — todo PDF baixado tem 0 bytes.
2. **`contratoGerado` marcado mesmo com PDF vazio**: `ContratoService.gerarContrato` seta a flag **antes** de saber se o PDF foi gerado; não verifica se já foi gerado antes.
3. **Conflito de nome com o módulo `contrato`**: aqui existe `ContratoService`/`PdfGeneratorService` (stub) enquanto o módulo `contrato` tem o fluxo completo + `ContratoPdfWriter` funcional — dois conceitos de "contrato" competindo.
4. **`FilePath` servido como `.jpg` cravado**: `DocumentoController.java:83-86` define `filename="comprovante_...jpg"` e `MediaType.IMAGE_JPEG` independente do tipo real armazenado.
5. **`criarEndpoints` sem cobertura de segurança** — nenhuma regra em `SecurityConfig` (ver 7.1).

## 6. Testes
Nenhum teste específico. Apenas `CrmApplicationTests` (smoke de contexto).

## 7. Dívidas Técnicas e Melhorias Recomendadas

### 7.1 Endpoints inacessíveis (SecurityConfig sem regra) — **[CRÍTICO] P1**
- `/api/v1/documentos/**` **não tem nenhum `requestMatchers`** no `SecurityConfig` — cai no `anyRequest().denyAll()` (`SecurityConfig.java:94`). Todo endpoint do módulo retorna **403/denyAll**.
- **Solução**: definir intenção de acesso (ex.: `@PreAuthorize` por papel, ou `authenticated`/`permitAll` por rota) e adicionar regra explícita no `SecurityConfig`; alinhar com o papel que precisa baixar contrato/recibo/comprovante.

### 7.2 Geração de PDF não implementada (stub) — **[CRÍTICO] P1**
- `PdfGeneratorService` retorna `byte[0]`: contratos/recibos baixados são **PDFs vazios**, e o `contratoGerado=true` mascara o problema.
- **Solução**: implementar com biblioteca real (OpenPDF/PDFBox/Flying Saucer) **ou** eliminar este módulo em favor do `contrato` (`ContratoPdfWriter` já funciona) — unificar a geração de PDF num serviço único (ver contrato/MODULE.md 7.1).

### 7.3 Escrita cross-module no `Agendamento` — **P1**
- `ContratoService.gerarContrato` seta `contratoGerado` via `AgendamentoRepository` (`ContratoService.java:24-26`) — o dono da máquina de estados do agendamento deve expor essa transição.
- **Solução**: o módulo `agenda` deve ser o dono do flag (por exemplo, método público `marcarContratoGerado` no `AgendamentoService`, ou evento `ContratoGeradoEvent` consumido pela agenda).

### 7.4 Duplicação de "comprovante" com o módulo contrato — **P2**
- `DocumentoController.downloadComprovante` (`:60-88`) duplica a lógica de servir comprovante do `ContratoController.downloadComprovante` (`contrato/api/ContratoController.java:111-127`), mas com magic strings `"entrada"`/`"final"` e extensão/metadata fixa.
- **Solução**: centralizar em `shared/storage` um serviço de upload/serve de comprovantes com content-type resolvido (`Files.probeContentType`).

### 7.5 `orElseThrow()` sem mensagem — **P2**
- `ContratoService.java:24,31` (gera `NoSuchElementException` crua).
- **Solução**: `AgendamentoNaoEncontradoException` (já usada no controller) + hierarquia central `BusinessException`.

### 7.6 Listener gera documento com efeito colateral no confirm — **P2**
- `DocumentoEventListener` roda no `AgendamentoConfirmadoEvent` e grava no banco — se o PDF for vazio, a flag ainda é gravada; sem `@Transactional` próprio e sem tratamento de erro (exceção do listener abortaria o publish do evento original? risco de efeito cascata).
- **Solução**: desacoplar (ouvir após commit / `@TransactionalEventListener(PHASE=AFTER_COMMIT`) e só marcar a flag quando o PDF real for gerado.

### 7.7 Sem filtros de hora/papel no controller — **P3**
- Endpoints sem `@Operation` sensível, sem validação de `tipo` via enum.

## 8. Exemplos de arquivos afetados
- `PdfGeneratorService.java:13-21` — retorna `byte[0]` (stub).
- `ContratoService.java:23-28,30-33` — escrita cross-module + `orElseThrow`.
- `DocumentoController.java:40-88` — endpoints bloqueados por segurança; filename `.jpg` cravado.
- `DocumentoEventListener.java:21-25` — gera documento vazio no confirm.
- `auth/config/SecurityConfig.java:94` — `anyRequest().denyAll()` deixa `/api/v1/documentos/**` sem regra.