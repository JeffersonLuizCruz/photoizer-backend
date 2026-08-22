# DEBT.md — Dívidas Técnicas Consolidadas (backend CRM Photoizer)

> Documento executivo consolidando as dívidas P1/P2/P3 identificadas nos `MODULE.md` dos 18 módulos (Fase 1 — investigação e documentação) com status de refactor da Fase 2 (aplicada ao módulo `agenda`).
> Detalhes e exemplos `arquivo:linha` em cada módulo: `src/main/java/com/photoizer/crm/{modulo}/MODULE.md`.

**Prioridades** → P1: bloqueia qualidade/segurança/escalabilidade. P2: qualidade, refatoração. P3: cosmético/complementar.

---

## 1. Top 10 — Dívidas **[CRÍTICO]** (resolver primeiro)

| # | Dívida | Módulo(s) | Status |
|---|--------|-----------|--------|
| 1 | **`/api/v1/documentos/**` inacessível**: sem regra no `SecurityConfig`, cai em `anyRequest().denyAll()` → módulo inteiro bloqueado | documento | Pendente |
| 2 | **IDOR em notificações**: `userId` vem da request sem verificação de dono (qualquer usuário lê/apaga notificações de outro) | notificacao | Pendente |
| 3 | ~~**Exposição de `User` com `password`** na API~~ | fotografo | **RESOLVIDO** |
| 4 | **PDF de contrato gerado "na mão"** (bytes nativos) + `PdfGeneratorService` do documento é **stub** (`byte[0]`) | contrato, documento | Pendente |
| 5 | ~~**Escrita cross-module**: serviços mutam entidades de outros módulos~~ | ~~ecommerce~~ | **RESOLVIDO** (ecommerce: eventos `CompraExtraFotosAssociadasEvent`, `CompraExtraCanceladaEvent`, `CompraExtraPagaEvent`, `FotoDownloadEvent`, `FotosSelecionadasEvent`, `TokenGaleriaRegeneradoEvent` + listeners `FotoEcommerceEventListener`, `AgendamentoEcommerceEventListener`) |
| 6 | **God classes** (`EcommerceService` 574, `FinanceiroService` 600, `FinanceiroDashboardService` 608, `AgendamentoService` 768, `EdicaoService` 534) | ecommerce, financeiro, agenda, edicao | Pendente |
| 7 | ~~**`findAll()` + agregação em memória** em dashboards/relatórios (tabelas crescentes)~~ | ~~dashboard~~, financeiro, comissao, indicador | **RESOLVIDO** (dashboard) |
| 8 | ~~**Vazamento de hash de senha do `Cliente`** nas respostas~~ | cliente | **RESOLVIDO** |
| 9 | ~~**`Entidade` JPA como contrato da API** (retorna entidade em vez de DTO)~~ | ~~fotografo~~ | **RESOLVIDO** (fotografo) |
| 10 | **Máquinas de estado sem validação central** (`if` espalhados, status como `String`) | agenda, contrato, comissao | Pendente |

---

## 2. Dívidas por módulo (P1)

### shared — infraestrutura transversal
- ~~Herança → composição~~ **RESOLVIDO**: `@Embeddable AuditInfo` + composição em todas as 25 entidades; `BaseEntity.java` removido.
- **Dependência invertida `shared → módulos`**: `GlobalExceptionHandler` importa exceções de todos os módulos (deveria ser os módulos → shared).
- Hierarquia de exceções ausente: falta `BusinessException` base + código + `HttpStatus` (padrões atuais: `RuntimeException`/`IllegalArgumentException` espalhadas).
- `ErrorResponse` sem suporte a múltiplos erros (`bindingResult`) e sem código de domínio.
- `handler(Exception.class)` genérico captura tudo; auditoria (`createdBy`) e testes ausentes (`CrmApplicationTests` é smoke).

### auth
- ~~`SecurityConfig` é god config~~ **RESOLVIDO**: migrado para `@RolesAllowed` nos controllers + `anyRequest().authenticated()`.
- ~~`User` sem Lombok/auditoria/consistência~~ **RESOLVIDO**: Lombok + `@Embeddable AuditInfo`.
- ~~Tratamento de erros inconsistente~~ **RESOLVIDO**: `ResponseStatusException(404/409)` + mensagem uniforme no login.
- ~~`LoginResponse.userId` como String~~ **RESOLVIDO**: trocado para `UUID`.
- ~~`User` expõe hash de senha~~ **RESOLVIDO**: `@JsonIgnore` + `FotografoController` retorna DTO.
- ~~**P2**: Token JWT 24h sem refresh/logout/revogação~~ **RESOLVIDO**: refresh token (7 dias) + blocklist + logout endpoint.
- ~~**P2**: Secret hardcoded em properties~~ **RESOLVIDO**: variável de ambiente `JWT_SECRET`.
- ~~**P2**: `FotografoService` duplica CRUD de User~~ **RESOLVIDO**: delega para `UserService`.

### cliente
- ~~**Contrato da API acoplado à entidade JPA** + **vazamento do hash de senha** nas respostas. Violações Modulith (services de ecommerce/agenda importados).~~ **RESOLVIDO**: DTOs (`ClienteResponse`, `ClienteAdminResponse`), `ClienteMapper`, `@Setter(PRIVATE)`, `TokenService` abstração, `AgendamentoClienteResponse` movido para agenda.

### comissao
- Controller dependente de **4 módulos** + `Map<String,Object>`; N+1 na listagem de indicadores; **financeiro cria `Indicacao` diretamente** (escrita cross-module — comissao deveria ser dono).

### config
- **RESOLVIDO**: `ConfigKey` enum centraliza chaves/tipos/defaults; `ConfiguracaoInvalidaException` valida valores; `@Cacheable`/`@CacheEvict` no service; DTOs (`ConfiguracaoRequest`/`ConfiguracaoResponse`); cross-module removido (endpoints de template movidos para `ContratoTemplateController` no módulo contrato); `DataSeeder` usa `ConfigKey`.

### contrato
- PDF manual (nakie lib); `ContratoFotografo` com `@ManyToOne User` (auth); estados validados por `if`; `listar` filtra em memória; **`EXPIRADO` nunca aplicado**; eventos `ContratoAssinado/Devolvido` sem consumidor.

### dashboard
- ~~`findAll()` em 3 pontos~~ **RESOLVIDO**: usa facades com queries agregadas SQL.
- ~~**7 repositórios de 6 módulos** sem facades~~ **RESOLVIDO**: 6 QueryService facades + FinanceCalculator.
- ~~regras financeiras (deslocamento/comissão/repasse) duplicadas com o financeiro~~ **RESOLVIDO**: `FinanceCalculator` compartilhado.
- **RESOLVIDO**: projeção `Object[]` → `RepasseAggregation` interface tipada.
- **RESOLVIDO**: `@Cacheable` em todos os 4 endpoints.

### despesa
- ~~Todas as exceções são `IllegalArgumentException`~~ **RESOLVIDO**: hierarquia `DespesaNaoEncontradaException` (404), `CategoriaDespesaNaoEncontradaException` (404), `CategoriaDuplicadaException` (409), `CategoriaEmUsoException` (409), `DespesaRecorrenteNaoPagaException` (422); `GlobalExceptionHandler` atualizado.
- ~~Soma de valores em memória (3 métodos)~~ **RESOLVIDO**: queries JPQL `COALESCE(SUM)` no `DespesaRepository`.
- ~~Duplicação queries recorrência~~ **RESOLVIDO**: `buscarRecorrentesVencidas()` compartilhado.
- ~~Acoplamento direto a `AgendamentoRepository`~~ **RESOLVIDO**: `DespesaAgendamentoGateway` (porta/ACL) + `AgendamentoGatewayAdapter`.
- **DTOs**: `DespesaMapper` (MapStruct) criado; controller usa mapper; `static of()` mantido para backward compat.
- **SRP**: `DespesaCategoriaService` extraído de `DespesaService` (312→~230 linhas).
- **Ordenação**: `DespesaSpecification.parseSort()` tipado centralizado.
- Job `@Scheduled` de recorrências junto do service (P3 — sem lock/idempotência).

### documento
- **Endpoints bloqueados** (`denyAll`); **PDF é stub**; escrita cross-module (`contratoGerado` no `Agendamento`); duplica `documento` vs `contrato`.

### ecommerce
- ~~God class (`EcommerceService`, +20 métodos)~~ **RESOLVIDO**: extraídos `CarrinhoService`, `FavoritoService`, `DownloadService`, `PagamentoExtraService`, `GaleriaQueryService`; `EcommerceService` agora é orquestrador fino (~300 linhas).
- ~~**Escrita directa em `FotoEnsaio`/`Agendamento`**~~ **RESOLVIDO**: eventos `CompraExtraFotosAssociadasEvent`, `CompraExtraCanceladaEvent`, `CompraExtraPagaEvent`, `FotoDownloadEvent`, `FotosSelecionadasEvent`, `TokenGaleriaRegeneradoEvent` + listeners `FotoEcommerceEventListener` (foto) e `AgendamentoEcommerceEventListener` (agenda). Removidas injeções de `AgendamentoRepository` e `FotoEnsaioRepository` dos services ecommerce.
- ~~`findAll().stream().filter` para achar fotos por `compraExtraId` (4 pontos)~~ **RESOLVIDO**: query dedicada `findByCompraExtraId(UUID)` no `FotoEnsaioRepository`.
- ~~Exceções genéricas `RuntimeException`/`IllegalArgumentException`~~ **RESOLVIDO**: 10 exceções de domínio criadas (`GaleriaNaoEncontradaException`, `CompraNaoEncontradaException`, `FotoNaoEncontradaException`, `CarrinhoVazioException`, `FotoJaSelecionadaException`, `FotoJaBaixadaException`, `LimitePacoteExcedidoException`, `CompraJaPagaException`, `SessaoInvalidaException`, `FotoIndisponivelException`).
- **P2 pendente**: `AdminAnalyticsController` ainda injeta repositórios diretamente no controller.
- **P2 pendente**: DTOs manuais (`static of()`, `.name()`) — migrar para MapStruct.
- **P3**: `Sessao`/`SessaoController` legado — `Sessao.status` migrado para enum `StatusSessao`, mas entidade ainda não integrada ao fluxo principal.
- **State Pattern** aplicado: `StatusCompraExtra` enum com métodos de transição (`proximoAoComprovanteEnviado`, `proximoAoCancelar`, `podeSerCancelada`).

### edicao
- **Escrita cross-module em `Agendamento`/`FotoEnsaio`**; service grande; dois fluxos de publicação duplicados (`publicarNoEcommerce` vs `publicarLoja`); 3 cópias de watermark/thumbnail.

### financeiro
- God classes; `findAll()` + Streams; **escrita directa** (`status` do `Agendamento`, `Indicacao`); controller expõe entidades (`Pagamento`, `FotoExtra`, `VideoExtra`); regra de partilha duplicada.

### foto
- Dependência **inversa** foto→agenda (exceção cruzada); expõe entidade JPA; `RuntimeException`; **publicação por escrita direta do edicao** (3ª cópia de watermark/thumbnail).

### fotografo
- ~~**`User` (com `password`) exposto via `/api/v1/fotografos`**~~ **RESOLVIDO**: agora retorna `UserResponse` (sem password).
- ~~CRUD de usuários duplicado com auth~~ **RESOLVIDO**: delega para `UserService`.
- Busca O(N)+N+1 nos relatórios; `RepasseController` atravessa o modulo agenda; partilha duplicada.

### indicador
- N+1 na listagem + acoplamento com comissao; exceções genéricas.

### notificacao
- **IDOR (ownership)**: qualquer autenticado manipula notificações de outro; listener atravessa repositório do agenda + acesso LAZY (`getCliente().getNome()` fora de transação).

### pacote
- Merge manual de 10 campos no `atualizar`.

### agenda
- God class; máquina de estados sem validação (`StatusAgendamento` sem methods); duplicação de cálculo financeiro; violações Modulith (repos/services de outros módulos); vazamento de web (`HttpServletRequest`/`MultipartFile`) na camada de serviço.
- **Fase 2 (refactor parcial concluído)** — ver `agenda/MODULE.md §7`:
  - ✅ Extraídos `AgendamentoStatusLifecycle`, `PartilhaService`, `DisponibilidadeService`, `AgendamentoValoresCalculator` (`AgendamentoService` 768→532 linhas).
  - ✅ Métodos de domínio em `Agendamento` (`transicionarPara`/`reagendar`/`aplicarPagamentoFinal`/`alternarDestaque`) e `AgendamentoFotografo` (`@Setter(PRIVATE)` + `atualizarRepasse`/`pagar`/`cancelar`).
  - ✅ Cálculo financeiro unificado + `valorRepasseEfetivo` único (`AgendamentoValoresCalculator`).
  - ✅ Queries renomeadas (`findActiveByLocalAndDataBetween`/`findActiveBetweenExcludingId`).
  - ✅ **MapStruct** adotado: `AgendamentoMapper` + `RascunhoAgendamentoMapper`; factories `of()` removidas das Responses.
  - ◐ Status: centralizado, mas transições inválidas ainda aceitas (decisão: encapsular sem bloquear).
  - ◐ Módulo `agenda` agora tem **acoplamento reverso**: `financeiro.FinanceiroService` e `cliente.ClienteController` importam o `AgendamentoMapper` (sem testes Modulith para bloquear).

---

## 3. Dívidas transversais (afetam quase todos os módulos)

| Padrão | Módulos afetados | Ação geral |
|--------|------------------|------------|
| ~~**Herança `BaseEntity`**~~ | ~~todos com entidades~~ | **RESOLVIDO**: `@Embeddable AuditInfo` + composição; `BaseEntity.java` removido |
| **`status`/`origem` em `String`** | comissao, agenda, foto, despesa, contrato, ecommerce | enums com métodos de transição; nunca comparar `String.equals` |
| **Exceções genéricas** | maioria | hierarquia central `BusinessException` + `HttpStatus`/código (decisão já aprovada) |
| **DTOs manuais (`static of`/`Map`)** | quase todos | MapStruct (decisão já aprovada; Fase 2) — **iniciado em `agenda`** (AgendamentoMapper/RascunhoAgendamentoMapper) |
| ~~**Escrita em entidade alheia** (ecommerce)~~ | ~~ecommerce, edicao, foto, financeiro, comissao, documento, notificacao~~ | **RESOLVIDO** (ecommerce): eventos de domínio + listeners; outros módulos pendentes |
| **Agregação em memória** | dashboard, financeiro, comissao, indicador, agenda | queries agregadas SQL (`SUM`/`GROUP BY`/`COUNT`) nos repositórios donos |

---

## 4. Próximos passos sugeridos (Fase 2)

Ordem proposta (valor × risco):

1. **Segurança imediata** — IDOR notificações, exposição de `password`/hash, `denyAll` documentos, exposição de entidades.
2. **Hierarquia de exceções** no `shared` + conversão das exceções genéricas (P1 dos módulos).
3. ~~**Padding cross-module** — substituir escritas diretas por eventos~~ **PARCIAL**: resolvido para ecommerce; pendente para edicao, foto, financeiro, comissao, documento, notificacao.
4. **Enums e máquinas de estado** — `StatusAgendamento`, `StatusIndicacao`, `StatusContrato` com transições.
5. **Queries agregadas** + facades públicas por módulo (dashboard/financeiro deixam de puxar repositório alheio).
6. **MapStruct total** nos DTOs.
7. ~~**AuditInfo `@Embeddable` + Auditing**~~ **RESOLVIDO**: `AuditInfo` criado, todas as 25 entidades migradas, `BaseEntity.java` removido.
8. **PDF unificado** (escolher lib; eliminar stub do documento e duplicação com contrato).

> **Fase 2 em andamento**: extração de services + encapsulamento de domínio + MapStruct foram aplicados ao módulo `agenda` (ver `agenda/MODULE.md §7`). As demais etapas desta lista ainda não foram executadas; as decisões arquiteturais (layering, exceções, MapStruct) já foram aprovadas em conversa anterior e estão refletidas nos `MODULE.md`.