# DEBT.md — Dívidas Técnicas Consolidadas (backend CRM Photoizer)

> Documento executivo consolidando as dívidas P1/P2/P3 identificadas nos `MODULE.md` dos 18 módulos (Fase 1 — investigação e documentação; **nenhuma alteração de código** foi feita).
> Detalhes e exemplos `arquivo:linha` em cada módulo: `src/main/java/com/photoizer/crm/{modulo}/MODULE.md`.

**Prioridades** → P1: bloqueia qualidade/segurança/escalabilidade. P2: qualidade, refatoração. P3: cosmético/complementar.

---

## 1. Top 10 — Dívidas **[CRÍTICO]** (resolver primeiro)

| # | Dívida | Módulo(s) |
|---|--------|-----------|
| 1 | **`/api/v1/documentos/**` inacessível**: sem regra no `SecurityConfig`, cai em `anyRequest().denyAll()` → módulo inteiro bloqueado | documento |
| 2 | **IDOR em notificações**: `userId` vem da request sem verificação de dono (qualquer usuário lê/apaga notificações de outro) | notificacao |
| 3 | **Exposição de `User` com `password`** na API | fotografo |
| 4 | **PDF de contrato gerado "na mão"** (bytes nativos) + `PdfGeneratorService` do documento é **stub** (`byte[0]`) | contrato, documento |
| 5 | **Escrita cross-module**: serviços mutam entidades de outros módulos (`Agendamento`, `FotoEnsaio`, `Indicacao`) diretamente | ecommerce, edicao, foto, financeiro, comissao, documento |
| 6 | **God classes** (`EcommerceService` 574, `FinanceiroService` 600, `FinanceiroDashboardService` 608, `AgendamentoService` 768, `EdicaoService` 534) | ecommerce, financeiro, agenda, edicao |
| 7 | **`findAll()` + agregação em memória** em dashboards/relatórios (tabelas crescentes) | dashboard, financeiro, comissao, indicador |
| 8 | **Vazamento de hash de senha do `Cliente`** nas respostas | cliente |
| 9 | **`Entidade` JPA como contrato da API** (retorna entidade em vez de DTO) | cliente, foto, financeiro, fotografo |
| 10 | **Máquinas de estado sem validação central** (`if` espalhados, status como `String`) | agenda, contrato, comissao |

---

## 2. Dívidas por módulo (P1)

### shared — infraestrutura transversal
- Herança → composição: todas as entidades usam `@MappedSuperclass BaseEntity` (ver padrão nos módulos). **[CRÍTICO — padrão vertical]**
- **Dependência invertida `shared → módulos`**: `GlobalExceptionHandler` importa exceções de todos os módulos (deveria ser os módulos → shared).
- Hierarquia de exceções ausente: falta `BusinessException` base + código + `HttpStatus` (padrões atuais: `RuntimeException`/`IllegalArgumentException` espalhadas).
- `ErrorResponse` sem suporte a múltiplos erros (`bindingResult`) e sem código de domínio.
- `handler(Exception.class)` genérico captura tudo; auditoria (`createdBy`) e testes ausentes (`CrmApplicationTests` é smoke).

### auth
- `SecurityConfig` é god config: mapeia rotas de **todos** os módulos (acoplamento forte e frágil a mudanças de path).
- `User` sem Lombok/auditoria/consistência (não estende `BaseEntity`); tratamento de erros inconsistente.

### cliente
- **Contrato da API acoplado à entidade JPA** + **vazamento do hash de senha** nas respostas. Violações Modulith (services de ecommerce/agenda importados).

### comissao
- Controller dependente de **4 módulos** + `Map<String,Object>`; N+1 na listagem de indicadores; **financeiro cria `Indicacao` diretamente** (escrita cross-module — comissao deveria ser dono).

### config
- `PUT /config` aceita qualquer `Map<String,String>` (chaves órfãs / `NumberFormatException`); `ConfiguracaoController` usa `ContratoTemplateService` (config→contrato); nomes de chaves duplicados (`percentualComissao` vs `comissaoPercentual`).

### contrato
- PDF manual (nakie lib); `ContratoFotografo` com `@ManyToOne User` (auth); estados validados por `if`; `listar` filtra em memória; **`EXPIRADO` nunca aplicado**; eventos `ContratoAssinado/Devolvido` sem consumidor.

### dashboard
- `findAll()` em 3 pontos; **7 repositórios de 6 módulos** sem facades; regras financeiras (deslocamento/comissão/repasse) duplicadas com o financeiro.

### despesa
- Todas as exceções são `IllegalArgumentException`; job `@Scheduled` de recorrências junto do service.

### documento
- **Endpoints bloqueados** (`denyAll`); **PDF é stub**; escrita cross-module (`contratoGerado` no `Agendamento`); duplica `documento` vs `contrato`.

### ecommerce
- God class (`EcommerceService`, +20 métodos); **escrita directa em `FotoEnsaio`/`Agendamento`**; `findAll().stream().filter` para achar fotos por `compraExtraId` (4 pontos).

### edicao
- **Escrita cross-module em `Agendamento`/`FotoEnsaio`**; service grande; dois fluxos de publicação duplicados (`publicarNoEcommerce` vs `publicarLoja`); 3 cópias de watermark/thumbnail.

### financeiro
- God classes; `findAll()` + Streams; **escrita directa** (`status` do `Agendamento`, `Indicacao`); controller expõe entidades (`Pagamento`, `FotoExtra`, `VideoExtra`); regra de partilha duplicada.

### foto
- Dependência **inversa** foto→agenda (exceção cruzada); expõe entidade JPA; `RuntimeException`; **publicação por escrita direta do edicao** (3ª cópia de watermark/thumbnail).

### fotografo
- **`User` (com `password`) exposto via `/api/v1/fotografos`**; busca O(N)+N+1 nos relatórios; CRUD de usuários duplicado com auth; `RepasseController` atravessa o modulo agenda; partilha duplicada.

### indicador
- N+1 na listagem + acoplamento com comissao; exceções genéricas.

### notificacao
- **IDOR (ownership)**: qualquer autenticado manipula notificações de outro; listener atravessa repositório do agenda + acesso LAZY (`getCliente().getNome()` fora de transação).

### pacote
- Merge manual de 10 campos no `atualizar`.

### agenda
- God class; máquina de estados sem validação (`StatusAgendamento` sem methods); duplicação de cálculo financeiro; violações Modulith (repos/services de outros módulos); vazamento de web (`HttpServletRequest`/`MultipartFile`) na camada de serviço.

---

## 3. Dívidas transversais (afetam quase todos os módulos)

| Padrão | Módulos afetados | Ação geral |
|--------|------------------|------------|
| **Herança `BaseEntity`** | todos com entidades | `@Embeddable AuditInfo` + JPA Auditing (eliminar `@MappedSuperclass`/`@SuperBuilder`) |
| **`status`/`origem` em `String`** | comissao, agenda, foto, despesa, contrato, ecommerce | enums com métodos de transição; nunca comparar `String.equals` |
| **Exceções genéricas** | maioria | hierarquia central `BusinessException` + `HttpStatus`/código (decisão já aprovada) |
| **DTOs manuais (`static of`/`Map`)** | quase todos | MapStruct (decisão já aprovada; Fase 2) |
| **Escrita em entidade alheia** | ecommerce, edicao, foto, financeiro, comissao, documento, notificacao | eventos de domínio (`publica`/`@EventListener`/`@TransactionalEventListener`) — padrão já usado corretamente em alguns pontos |
| **Agregação em memória** | dashboard, financeiro, comissao, indicador, agenda | queries agregadas SQL (`SUM`/`GROUP BY`/`COUNT`) nos repositórios donos |

---

## 4. Próximos passos sugeridos (Fase 2)

Ordem proposta (valor × risco):

1. **Segurança imediata** — IDOR notificações, exposição de `password`/hash, `denyAll` documentos, exposição de entidades.
2. **Hierarquia de exceções** no `shared` + conversão das exceções genéricas (P1 dos módulos).
3. **Padding cross-module** — substituir escritas diretas por eventos (agenda dono do estado; comissao dono de `Indicacao`) e remover dependências invertidas (`shared→módulos`, `foto→agenda`).
4. **Enums e máquinas de estado** — `StatusAgendamento`, `StatusIndicacao`, `StatusContrato` com transições.
5. **Queries agregadas** + facades públicas por módulo (dashboard/financeiro deixam de puxar repositório alheio).
6. **MapStruct total** nos DTOs.
7. **AuditInfo `@Embeddable` + Auditing** (substitui `BaseEntity`).
8. **PDF unificado** (escolher lib; eliminar stub do documento e duplicação com contrato).

> Nenhuma etapa desta lista foi executada; as decisões arquiteturais (layering, exceções, MapStruct) já foram aprovadas em conversa anterior e estão refletidas nos `MODULE.md`.