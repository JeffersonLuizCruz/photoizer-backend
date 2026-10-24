# Padrão de Documentação de API (OpenAPI / Swagger)

Documento de referência para documentar a API do CRM Photoizer seguindo as
boas práticas de mercado. Vale para todo endpoint público em `/api/v1`.

## 1. Stack e compatibilidade

| Item | Versão | Observação |
|------|--------|------------|
| Spring Boot | 4.1.0 | linha v4 |
| `springdoc-openapi-starter-webmvc-ui` | **3.1.1** | **SpringDoc v3 é obrigatório para Spring Boot 4**; a linha v2.x atende apenas Spring Boot 3.x |
| OpenAPI gerado | 3.1.0 | saída de `/v3/api-docs` |

Ao subir o Spring Boot, suba o SpringDoc para a mesma linha (v3 para Boot 4).
A versão 3.1.1 é buildada contra o próprio Boot 4.1.0 — manter essa correlação.

## 2. Onde fica cada coisa

- `shared/config/OpenApiConfig.java` — metadados (`Info`, `servers`), esquemas de
  segurança (`bearerAuth`, `cookieAuth`) e componentes reutilizáveis
  (`ErrorResponse`, `PageResponse`).
- `shared/config/openapi/OpenApiTags.java` — **catálogo canônico de tags** (nome,
  descrição e ordem). Fonte de verdade dos grupos exibidos no Swagger UI.
- `shared/config/openapi/OpenApiTagsCustomizer.java` — substitui a lista final de
  tags pelo catálogo (evita duplicatas quando a descrição do `@Tag` do controller
  diverge da canônica e garante ordem determinística).
- `shared/config/openapi/GlobalOpenApiCustomizer.java` — aplica, a **todos** os
  endpoints: requisito de segurança (quando há `@RolesAllowed`), respostas padrão
  `401`/`403` (endpoints protegidos), `422` (endpoints com `@RequestBody`) e `500`.
- `docs/api/openapi.json` — especificação versionada, gerada a partir do código.

## 3. Convenções de anotação

### Controller
- `@Tag(name = OpenApiTags.X)` — use o nome exato do catálogo (ou a mesma string).
  A descrição canônica vem do catálogo central.
- `@Operation(operationId = "...", summary = "...", description = "...")`
  - `operationId` **único no mundo** (prefixar com o recurso: `criarCliente`,
    `buscarClientePorId`). Evite nomes genéricos (`criar`, `buscarPorId`) que
    colidem entre controllers.
  - `summary` curto (imperativo); `description` para regras de negócio.
- `@ApiResponses` — documente os status **específicos** do endpoint (201, 404, 409,
  422…). As respostas transversais (`401`/`403`/`500`) já entram pelo customizer.
  Referencie o contrato de erro com
  `content = @Content(schema = @Schema(implementation = ErrorResponse.class))`.
- `@Parameter(description = "...")` em todo `@PathVariable`/`@RequestParam`.
- `@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_BEARER)` (+ `_COOKIE`)
  apenas em endpoints autenticados que **não** declaram `@RolesAllowed` na classe
  (ex.: `/auth/me`, `/auth/cliente/perfil`).
- `@Hidden` para controllers internos (ex.: `DevPagamentoController`).

### DTOs (Java Records)
- `@Schema(name = "...", description = "...")` na record.
- `@Schema(description = ..., example = ...)` nos campos; `allowableValues` para
  enums; `nullable = true` em campos opcionais.
- Evite `example` em campos `LocalDateTime`/`OffsetDateTime` (o swagger-core emite
  aviso de serialização JSR-310). Descreva o formato na `description`.

### Evite
- Retornar entidade JPA (vaza proxy/lazy e acopla o contrato ao schema). Use DTO.
- Corpo genérico `Map<String,String>` — crie um record tipado
  (ver `AtualizarStatusRequest`).

## 4. Contrato de erro

Todas as respostas de erro seguem `ErrorResponse`
(`shared/exception/ErrorResponse.java`):

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Cliente não encontrado",
  "code": "CLIENTE_NAO_ENCONTRADO",
  "timestamp": "2026-01-01T12:00:00Z",
  "correlationId": "9f1c2d3e-4a5b-6c7d-8e9f-0a1b2c3d4e5f",
  "fieldErrors": [ { "field": "email", "message": "deve ser um e-mail válido" } ]
}
```

- `code` corresponde a uma constante de `shared/exception/ErrorCode.java`.
- O `GlobalExceptionHandler` mapeia a hierarquia `BusinessException` para os status
  HTTP; use as exceções de domínio para que o `code` seja preenchido.
- `correlationId` é o ID de correlação da requisição e é o **mesmo valor** do header
  `X-Correlation-ID` da resposta. O cliente deve exibi-lo em mensagens de erro e
  enviá-lo em chamados de suporte; a equipe usa esse ID para localizar todos os
  logs daquela requisição (inclusive stack traces).
- O header `X-Correlation-ID` também é aceito **na entrada**: se o cliente enviar
  um valor válido (até 64 chars, `[A-Za-z0-9._-]`), ele é reutilizado; caso
  contrário, o servidor gera um UUID.
- Erros de segurança (401/403), CSRF e rate limit (429) também seguem este
  contrato — não há mais `sendError` devolvendo o body padrão do container.

## 5. Segurança documentada

- Endpoints protegidos são detectados automaticamente pelo
  `GlobalOpenApiCustomizer` quando há `@RolesAllowed` (na classe ou no método).
- Requisitos manuais: endpoints autenticados sem papel específico (ver §3).
- Exposição da UI/spec por ambiente (ver `SecurityConfig`):

| Perfil | Swagger UI / `/v3/api-docs` | H2 console |
|--------|------------------------------|------------|
| dev/local/test | livre (`permitAll`) | livre |
| homolog | staff autenticado | negado |
| prod | negado (`denyAll`) | negado |

Em prod, consumidores usam `docs/api/openapi.json` versionado.

## 6. Geração da spec e CI

```bash
# Gera/atualiza docs/api/openapi.json (usa H2; não exige Postgres)
mvn -Dtest=OpenApiSpecExporter test
```

A spec é **determinística**: rode o comando e commite o arquivo junto do código.

O pipeline de CI (`mvn -B verify`) executa o `OpenApiContractTest`, que falha se a
spec não gerar ou perder invariantes (título, `ErrorResponse`, esquemas de
segurança, presença de endpoints). O job `openapi` ainda regenera a spec e falha se
`docs/api/openapi.json` estiver desatualizado.

## 7. Checklist para um novo endpoint

- [ ] `@Operation(operationId` único, `summary`) e `description` se tiver regra.
- [ ] `@ApiResponses` para 2xx e erros de negócio específicos.
- [ ] `@Parameter` em path/query.
- [ ] DTO de request/response com `@Schema` (nome, descrição, exemplo).
- [ ] `@RolesAllowed` quando protegido (senão, `@SecurityRequirement` manual).
- [ ] `mvn -Dtest=OpenApiSpecExporter test` e commit da spec.

## 8. Backlog conhecido (fora do piloto)

- `operationId` ainda colide em controllers não migrados: atribuir `operationId`
  único a todos os 176 endpoints.
- `AdminComprasController.confirmar` não declara `{agendamentoId}` na assinatura.
- Cobertura de `@Schema` completa nos DTOs dos módulos fora do piloto
  (auth/financeiro/ecommerce/despesa/etc.).
