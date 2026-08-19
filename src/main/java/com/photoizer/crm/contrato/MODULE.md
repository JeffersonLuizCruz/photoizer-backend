# Módulo: Contrato

## 1. Responsabilidade
Gerencia todo o ciclo de vida de contratos de prestação de serviços: criação a partir do pacote, **publicação** com token público de assinatura, **assinatura digital** pelo cliente (upload de comprovante da reserva + snapshot imutável com hash), confirmação de pagamento, aprovação (que materializa o agendamento via evento) e devolução/cancelamento. Também gera o PDF do contrato.

## 2. Estrutura
```
contrato/
├── model/
│   ├── Contrato.java          # Entidade (extends BaseEntity): snapshot do serviço (pacote+cliente+fotógrafos+financeiro), ~30 campos, token assinatura, snapshot/hash, datas
│   ├── StatusContrato.java    # Enum: RASCUNHO, PUBLICADO, ASSINADO_PELO_CLIENTE, PAGAMENTO_CONFIRMADO, APROVADO, DEVOLVIDO, CANCELADO, EXPIRADO
│   ├── Assinatura.java        # Entidade (extends BaseEntity): contratoId (UUID solto, unique), nomeAssinante, ip, hash — 1 assinatura por contrato
│   └── ContratoFotografo.java # Entidade p/ fotógrafos do ensaio (@ManyToOne Contrato + @ManyToOne User do módulo auth), tipoValor/percentual/valorRepassar
├── repository/
│   ├── ContratoRepository.java  # JpaRepository + findByTokenHash, findByStatus
│   └── AssinaturaRepository.java # JpaRepository + findByContratoId
├── service/
│   ├── GestaoContratoService.java  # 326 linhas: criar/publicar/confirmarPagamento/aprovar/devolver/cancelar/listar/vincularAgendamento
│   ├── ContratoPublicoService.java # 359 linhas: buscarPublico/status/assinar (snapshot+hash+PDF+assinatura); JSON manual
│   ├── ContratoTemplateService.java # carrega template da config e renderiza placeholders {{...}} (texto e HTML)
│   └── ContratoPdfWriter.java      # 174 linhas: gerador de PDF "na mão" (bytes PDF nativo, sem lib)
├── api/
│   ├── ContratoController.java        # CRUD + publicar + confirmar-pagamento + aprovar + devolver + cancelar + download PDF/comprovante
│   ├── ContratoPublicoController.java # endpoints públicos por token (carregar/status/assinar multipart)
│   └── DTOs: ContratoResponse, ContratoPublicoResponse, ContratoStatusPublicoResponse, CriarContratoRequest, DevolverContratoRequest, PublicarContratoResponse
├── event/
│   ├── ContratoAprovadoEvent.java  # publica quando aprovado (26 campos — produz agendamento na agenda)
│   ├── ContratoAssinadoEvent.java  # publica ao assinar — SEM consumidor
│   └── ContratoDevolvidoEvent.java # publica ao devolver — SEM consumidor
└── exception/
    ├── ContratoNaoEncontradoException.java
    ├── ContratoEstadoInvalidoException.java
    └── ContratoTokenExpiradoException.java
```

## 3. Dependências Externas

### Módulos internos importados — **[VIOLAÇÕES Modulith]**
| Módulo | Onde | Uso |
|--------|------|-----|
| **auth** | `ContratoFotografo.java:3-4,43-44` | entidade com `@ManyToOne User` + `Papel` — acoplamento forte de entidades |
| **auth** | `GestaoContratoService:22,48,152` | `UserRepository.findById` para validar editor/fotógrafo |
| **pacote** | `GestaoContratoService:19-21,47,65-70` | `PacoteRepository` + exceções `Pacote*` |
| **agenda** | `GestaoContratoService:3-5` | exceções `AgendamentoNoPassadoException`, `EditorNaoEncontradoException`, `FotografoNaoEncontradoException` |
| **config** | `GestaoContratoService`, `ContratoPublicoService`, `ContratoTemplateService` | `ConfiguracaoService` p/ `taxaDeslocamentoPadrao`, `percentualEntrada`, `contratoDiasValidade`, `contratoTemplateTexto`, dados da contratada/PIX |
| **shared** | model/api/service | `BaseEntity`, `TipoRepasse`, `FileStorageService` |

### Módulos que dependem deste — **[VIOLAÇÕES]**
| Módulo | Uso |
|--------|-----|
| **agenda** | `ContratoAprovadoEventListener` (agenda/listener) consome `ContratoAprovadoEvent` → cria agendamento; **também injeta `ContratoRepository` e grava `agendamentoId` de volta** (escrita cross-module) |
| **config** | `ConfiguracaoController` injeta `ContratoTemplateService` (service de outro módulo) para editar template |
| **shared** | `GlobalExceptionHandler` mapeia as 3 exceções |

### Eventos publicados
| Evento | Consumidor |
|--------|-----------|
| `ContratoAprovadoEvent` | `agenda:ContratoAprovadoEventListener` (cria agendamento via `AgendamentoService.criarAgendamentoDeContrato`) — **bom uso de evento** |
| `ContratoAssinadoEvent` | **ninguém** — código morto (não notifica financeiro/notificacao) |
| `ContratoDevolvidoEvent` | **ninguém** — código morto |

### Segurança (SecurityConfig)
- `/api/v1/contratos/publico/**` → `permitAll` (assinatura pública por token) — correto.
- `/api/v1/contratos/**` → `authenticated`.

## 4. Fluxos Principais

### Fluxo 1: Criação e Publicação
1. `POST /api/v1/contratos` → `GestaoContratoService.criar` (`:64-126`): carrega pacote, valida ativo/editor/data, calcula deslocamento/entrada (fator `percentualEntrada`), resolve fotógrafos (`resolverFotografos:128-148`, valida soma de repasses ≤ valorTotal) e grava como `RASCUNHO`.
2. `POST /{id}/publicar` (`:172-194`): gera `token = UUID`, guarda `tokenHash = sha256(token)`, seta `PUBLICADO` + `tokenExpiracao = now + contratoDiasValidade` (default 7).

### Fluxo 2: Assinatura Pública (cliente)
1. `GET /api/v1/contratos/publico/{token}` → `buscarPublico` (`ContratoPublicoService:62-87`): busca por hash, valida expiração, renderiza cláusulas via template de config.
2. `POST /publico/{token}/assinar` (`:142-207`): valida campos/comprovante (PDF/JPG/PNG), salva comprovante (`fileStorageService.salvarEmSubdiretorio`), monta **snapshot JSON manual**, calcula **hash SHA-256**, gera **PDF nativo**, grava `Assinatura` (1 por contrato), popula dados do cliente no contrato, seta `ASSINADO_PELO_CLIENTE`, publica `ContratoAssinadoEvent`.

### Fluxo 3: Confirmação → Aprovação → Agendamento
1. `POST /{id}/confirmar-pagamento` (`GestaoContratoService:219-227`): exige `ASSINADO_PELO_CLIENTE` → `PAGAMENTO_CONFIRMADO`.
2. `POST /{id}/aprovar` (`:229-279`): exige `PAGAMENTO_CONFIRMADO` → `APROVADO`, publica `ContratoAprovadoEvent` com repasses dos fotógrafos.
3. `agenda:ContratoAprovadoEventListener` cria `Agendamento` a partir do evento e **grava `agendamentoId` no contrato via `ContratoRepository`**.
4. `POST /{id}/devolver` (`:281-298`): exige `ASSINADO_PELO_CLIENTE` ou `PAGAMENTO_CONFIRMADO` → `DEVOLVIDO` + motivo; publica evento (sem consumidor).
5. `POST /{id}/cancelar` (`:300-309`): bloqueado após `APROVADO`/`CANCELADO`.

### Fluxo 4: Downloads
- `GET /{id}/pdf` e `/{id}/comprovante` (`ContratoController:94-127`) servem `FileSystemResource` a partir do caminho armazenado, com `Content-Disposition` e `probeContentType`.

## 5. Regras Específicas
1. **Snapshot imutável**: ao assinar, tudo é congelado em `snapshotJson` + `snapshotHash` (integridade) + PDF — boa prática.
2. **Máquina de estados validadas por `if` espalhado** nos services, não por método de transição no enum/entidade (comparar com `StatusAgendamento` — mesma dívida).
3. **`EXPIRADO` nunca é aplicado**: `validarExpiracao` (`ContratoPublicoService:214-222`) só rejeita se HOJE > expiração e o status ainda for `PUBLICADO`/`DEVOLVIDO`; **não existe job/scheduler** que marque contratos como `EXPIRADO`, então o estado é código morto.
4. **1 assinatura/contrato**: `Assinatura` com `unique = contrato_id`; token buscado apenas por hash (`findByTokenHash`), nunca por texto — bom.
5. **Reassinar após devolução**: `assinar` aceita `PUBLICADO` ou `DEVOLVIDO` e limpa motivo/devolução.
6. **Repasse** via `TipoRepasse` (FIXO/PERCENTUAL), soma de repasses não pode exceder `valorTotal` (`resolverFotografos:139-146`).
7. **Contrato denormalizado**: duplica dados de `Pacote` (nome, valores, precoFotoExtra) e do cliente (nome, telefone, cpf...) — a intenção do snapshot é clara, mas a criação duplica manualmente.

## 6. Testes
Nenhum teste específico. Apenas `CrmApplicationTests` (smoke de contexto).

## 7. Dívidas Técnicas e Melhorias Recomendadas

### 7.1 PDF gerado "na mão" — **[CRÍTICO] P1**
- `ContratoPdfWriter` escreve os bytes do PDF manualmente (`montarPdf`, `montarConteudo`), com encoding WinAnsi e quebra de linha própria — frágil, sem suporte a layout/fonte/acentuação confiável.
- **Solução**: usar biblioteca (OpenPDF/PDFBox/Flying Saucer). Também existe **duplicação com o módulo `documento`** (`PdfGeneratorService`) — unificar em um serviço de PDF no `shared`/`documento`.

### 7.2 Entidade `ContratoFotografo` acoplada a `User` (auth) — **[CRÍTICO] P1**
- `@ManyToOne User` + `Papel` (`ContratoFotografo.java:37-56`) criam dependência de entidade entre módulos.
- **Solução**: armazenar `fotografoId` (UUID solto, como `Contrato.agendamentoId`) + `nomeFotografo`/`papel` por snapshot, e resolver o `User` apenas na leitura via evento/DTO do auth.

### 7.3 Máquina de estados sem transição central — **P1**
- `if (status != X) throw` repetido em `publicar`/`confirmarPagamento`/`aprovar`/`devolver`/`cancelar` — regra dispersa.
- **Solução**: métodos de domínio no `Contrato` (`publicar(token, exp)`, `confirmarPagamento()`, `aprovar()`, `devolver(motivo)`, `cancelar()`) que validam e mudam o status; elimina `ContratoEstadoInvalidoException` duplicada em cada service.

### 7.4 `listar` filtra e ordena em memória — **P1**
- `GestaoContratoService.listar` (`:202-217`) faz `findAll()`/`findByStatus` e aplica `search` + `sort` em Java.
- **Solução**: query com `LIKE`/`Pageable` no repositório.

### 7.5 Eventos órfãos + primeiras transições não auditadas — **P2**
- `ContratoAssinadoEvent` e `ContratoDevolvidoEvent` publicados sem consumidor — perde-se a chance de notificar financeiro/notificacao (o módulo `notificacao` existe e não é usado aqui).
- `ContratoAprovadoEvent` carrega **26 campos** — evento "gordo"; aceitável para materializar o agendamento, mas frágil frente a mudanças.

### 7.6 JSON de snapshot construído manualmente — **P2**
- `ContratoPublicoService.montarSnapshot`/`toJson`/`escapeJson` (`:243-306`) reimplementam serialização.
- **Solução**: usar o próprio Jackson (já no classpath via Spring) com `ObjectMapper`.

### 7.7 Exceções `IllegalArgumentException`/`IllegalStateException` — **P2**
- `validarCamposCliente`/`validarComprovante` (`ContratoPublicoService:224-241`) e `resolverFotografos` (`GestaoContratoService:143`), `gravarPdf` (`:316`), `sha256` (`:323`).
- **Solução**: hierarquia central `BusinessException` + `BadRequestException`/`PdfGenerationException`.

### 7.8 Duplicação de regras financeiras (entrada/repasse) — **P2**
- Cálculo de `percentualEntrada`/`valorEntradaExigido` (`GestaoContratoService:89-93`) repetido no financeiro/agenda; `valorRepasseEfetivo` (`:164-170`) também existe em outros módulos.
- **Solução**: `FinanceCalculator`/`PartilhaCalculator` compartilhado (ver financeiro/MODULE.md 7.5).

### 7.9 Acesso a arquivo por caminho armazenado — **P3**
- `downloadPdf`/`downloadComprovante` (`ContratoController:101,118`) fazem `Path.of(contrato.getUrlPdf())` — caminho é interno, mas validar contra o diretório de uploads (`FileStorageService`) reduz risco de path traversal se o banco for adulterado.

### 7.10 Herança `BaseEntity` — **P1** (padrão-aplicável)
- `Contrato`, `Assinatura`, `ContratoFotografo` estendem `@MappedSuperclass`.
- **Solução**: `@Embeddable AuditInfo` + Auditing.

## 8. Exemplos de arquivos afetados
- `ContratoPdfWriter.java:28-32,77-139` — geração manual de PDF (deve trocar por lib).
- `ContratoFotografo.java:36-56` — `@ManyToOne User` (acoplamento auth).
- `GestaoContratoService.java:64-126,172-309` — lógica de estados com `if`s; `:202-217` filtro em memória; `:229-279` evento de 26 campos.
- `ContratoPublicoService.java:142-207,243-306` — assinatura, snapshot/hash manual, JSON manual, `IllegalArgumentException`.
- `agenda/listener/ContratoAprovadoEventListener.java:26-36` — consome evento (bom) mas escreve no `ContratoRepository` (violação).
- `config/api/ConfiguracaoController.java:21-60` — injeta `ContratoTemplateService` (violação config→contrato).
- `StatusContrato.java:3-12` — `EXPIRADO` sem automação.