# Runbook de Diagnóstico por Logs — CRM Photoizer

> Guia prático para usar `correlationId`, `userId` e `traceId` na investigação de
> bugs. Complementa [`LOGGING.md`](./LOGGING.md) (arquitetura e catálogo completo
> de eventos).

## 1. Objetivo

Transformar uma reclamação (“não consegui confirmar a proposta”, “deu erro 500”,
“está lento”) em uma linha do tempo objetiva, usando os identificadores que o
backend já publica em **todos** os logs.

Público: suporte, QA, desenvolvimento e operação.

## 2. Os três identificadores

| Campo no log | Exemplo | O que é | Para que serve |
|---|---|---|---|
| `[5de952bc-b63b-49a9-8740-971f46b82203]` | `correlationId` | ID lógico da **requisição HTTP**. Gerado pelo `CorrelationIdFilter` ou reutilizado do header `X-Correlation-ID`. | Agrupar todas as linhas de uma mesma requisição: access log, services, eventos e erro. É o ID principal do suporte. |
| `[user=9a3ebda5-4770-4b14-80bb-57814e0e0052]` | `userId` | UUID interno do usuário autenticado (subject do JWT). | Saber **quem** disparou a requisição, validar permissão e ver o histórico do usuário. |
| `[trace=6add1fd7ee34bf20ee7895e605589e42]` | `traceId` | ID do **trace distribuído** (Micrometer/Brave, 128 bits em hex). O `spanId` (16 hex) aparece no JSON. | Correlacionar com o APM (Datadog/Zipkin/OTel), ver waterfall de spans e latência por etapa. |

### Como se relacionam

```
Usuário 9a3ebda5...
  └── Requisição HTTP  GET /api/v1/agenda
        correlationId = 5de952bc...   (uma por requisição)
        traceId       = 6add1fd7...   (uma por trace; pode incluir vários requests/serviços)
        spanId        = ...           (uma por operação dentro do trace)
```

- `correlationId` é **do aplicativo** (suporte/negócio).
- `traceId` é **do APM** (observabilidade técnica).
- `userId` é o elo com o **negócio**.
- IDs de domínio (`agendamento_id`, `compra_id`, `cliente_id`, `foto_id`) são o elo
  quando o problema atravessa várias requisições.

## 3. Onde encontrar cada ID

| Local | `correlationId` | `userId` | `traceId` |
|---|---|---|---|
| Console (dev) | `[5de952bc-...]` | `[user=...]` | `[trace=...]` |
| JSON (homolog/prod) | `correlationId` | `userId` (+ `userRole`) | `traceId` e `spanId` |
| Response header | `X-Correlation-ID` | não vai no header | `traceparent` (se propagação W3C habilitada) |
| Corpo de erro | `correlationId` | não vai no corpo | não vai no corpo |
| APM | atributo customizado (se configurado) | atributo customizado | trace/span nativo |

O CORS expõe `X-Correlation-ID`, então o frontend consegue lê-lo no DevTools
(Network → Response Headers) e exibir no toast de erro.

## 4. Anatomia de uma linha útil

Console:

```
2026-10-24 17:52:32.055 INFO  c.p.c.s.logging.RequestLoggingFilter [5de952bc-...] [user=9a3ebda5-...] [trace=6add1fd7...] - GET /api/v1/agenda -> 500 (840 ms)
```

JSON equivalente:

```json
{
  "@timestamp": "2026-10-24T20:52:32.055Z",
  "level": "INFO",
  "logger": "com.photoizer.crm.shared.logging.RequestLoggingFilter",
  "message": "GET /api/v1/agenda -> 500 (840 ms)",
  "correlationId": "5de952bc-...",
  "userId": "9a3ebda5-...",
  "userRole": "ROLE_AGENDADOR",
  "traceId": "6add1fd7ee34bf20ee7895e605589e42",
  "spanId": "ee7895e605589e42",
  "http_method": "GET",
  "http_path": "/api/v1/agenda",
  "http_status": 500,
  "duration_ms": 840,
  "http_route": "/api/v1/agenda"
}
```

Linha de erro da mesma requisição:

```json
{
  "level": "ERROR",
  "logger": "com.photoizer.crm.shared.exception.GlobalExceptionHandler",
  "message": "Erro interno não tratado",
  "correlationId": "5de952bc-...",
  "error.stack_trace": "java.lang.NullPointerException ..."
}
```

## 5. Fluxo de debugging em 6 passos

### Passo 1 — Capturar o `correlationId`

Em ordem de preferência:

1. Corpo do erro (`correlationId`) ou header `X-Correlation-ID` no DevTools.
2. Access log próximo do horário + `http_path` + `userId`.
3. Se só houver print/hora: filtrar por `userId` + janela de tempo + rota.

> Se o frontend ainda não mostra o código, use o header da resposta ou o horário
> exato do request. Ideal: exibir no toast com botão “copiar” (ver §10).

### Passo 2 — Filtrar a requisição

```text
correlationId:"5de952bc-b63b-49a9-8740-971f46b82203"
```

Ordene por `@timestamp` ascendente. Você verá o access log, os eventos de negócio
(`event_action`) e o eventual stack trace.

### Passo 3 — Montar a linha do tempo

1. **Access log**: endpoint, status e duração.
2. **Último `event_action` antes do erro**: onde o fluxo parou.
3. **IDs de negócio**: `agendamento_id`, `compra_id`, `foto_id`.
4. **Erro**: `WARN` (regra de negócio) ou `ERROR` (falha técnica).

### Passo 4 — Classificar o problema

| Status | Nível esperado | Significado | Onde olhar |
|---|---|---|---|
| 400/422 | WARN | payload/validação | `fieldErrors` no corpo + `event_action` |
| 401 | DEBUG/INFO | não autenticado/token expirado | access log + `SecurityErrorHandler` (401 em DEBUG) |
| 403 | WARN | papel/CSRF | `security.csrf_invalido` ou `SecurityErrorHandler` |
| 404 | WARN | recurso inexistente | campo `code` do `ErrorResponse` |
| 409 | WARN | conflito de negócio | `agendamento.conflito`, `DataIntegrityViolation` |
| 429 | WARN | rate limit | `security.rate_limit_excedido` |
| 500 | ERROR | falha técnica | stack trace + último `event_action` |

### Passo 5 — Aprofundar

- **É do usuário?** Filtre por `userId` e veja se repete.
- **É do fluxo?** Filtre por `event_action` + ID de negócio em janela maior.
- **É de permissão?** Confira `userRole` e a rota.
- **É de performance?** Use `duration_ms` + `traceId` no APM.
- **É de consistência?** Siga a cadeia de eventos (ex.: pagamento).

### Passo 6 — Reproduzir

O backend reutiliza `X-Correlation-ID` válido. Para não colidir com o incidente
original, use um sufixo:

```bash
curl -i -H 'X-Correlation-ID: 5de952bc-b63b-49a9-8740-971f46b82203-repro' ...
```

Em dev, suba com `com.photoizer.crm=DEBUG` para ver argumentos mascarados dos
services (sem PII). Compare `event_action` e status.

## 6. Receitas por tipo de bug

### 6.1 “Não consigo confirmar a proposta”

1. `correlationId` do erro.
2. Access log: `POST /api/v1/agendamentos/... -> 409/422`.
3. Procure `agendamento.conflito` ou `agendamento.status_alterado` com `agendamento_id`.
4. Confira o `code` do `ErrorResponse` e o status no banco.
5. Se conflito de agenda: veja `fotografo_id`, `data_hora` e o bloqueio/agendamento concorrente.

### 6.2 “Pagamento não apareceu no financeiro”

1. Busque por `compra_extra_id` ou `agendamento_id` (o fluxo pode começar em outra requisição).
2. Siga a cadeia:
   - `ecommerce.pagamento_confirmado`
   - `financeiro.compra_extra_confirmada`
   - `financeiro.pagamento_extra_registrado`
   - `agenda.pagamento_registrado`
   - `agendamento.pagamento_final_registrado`
3. Se parou no meio, procure `ERROR` no mesmo `traceId` (listener síncrono lançou exceção).
4. Verifique `financeiro.pagamento_duplicado` (idempotência).

### 6.3 “Erro 500 em produção”

1. Filtre `correlationId`.
2. Localize `ERROR` do `GlobalExceptionHandler` com `error.stack_trace`.
3. O `event_action` imediatamente anterior indica o service que falhou.
4. Pegue `userId` e `traceId`; no APM, veja o span que falhou.
5. Reproduza em dev com DEBUG e o mesmo payload/IDs.

### 6.4 “Usuário tomou 403”

1. Access log: `http_status:403`.
2. Procure `security.csrf_invalido` (CSRF) ou `SecurityErrorHandler` 403 (role).
3. Confira `userRole` no JSON e a rota (`@RolesAllowed`/`requestMatchers`).
4. Se CSRF: verifique `X-XSRF-TOKEN` e o cookie `XSRF-TOKEN`.

### 6.5 “Token expirado / refresh falhando”

1. `http_status:401` + `correlationId`.
2. Procure `auth.refresh_token_reuso` (WARN) — reuso revoga a família.
3. 401 é logado em DEBUG; se precisar do motivo, suba temporariamente:
   `com.photoizer.crm.shared.exception.SecurityErrorHandler=DEBUG`.
4. Confira `userId` (se autenticado) e a expiração do token.

### 6.6 “Requisição lenta”

1. Access log com `duration_ms` alto.
2. Use `traceId` no APM para o waterfall (spans HTTP/JDBC, se habilitados).
3. Agrupe por `http_route` para saber se é endpoint específico.
4. Compare com volume (`count`) no mesmo período.

### 6.7 “Notificação não chegou”

1. Busque `notificacao.criada` com `agendamento_id`.
2. Se não existir, o evento não chegou ao listener; procure o `event_action` anterior
   (`agendamento.aprovado`, `agendamento.pagamento_final_registrado`).
3. Se existir, a notificação foi criada no banco; o problema pode ser na leitura
   (`GET /api/v1/notificacoes`) ou no frontend.

### 6.8 “Upload de foto falhou”

1. `foto.upload_concluido` (sucesso) ou `foto.imagem_invalida` / `foto.marca_dagua_falhou` (falha).
2. Use `agendamento_id` e `foto_id`.
3. `imagem_invalida` é 422 (WARN); `marca_dagua_falhou` é ERROR com stack.

## 7. Eventos mais úteis (`event_action`)

| Domínio | Eventos |
|---|---|
| Agenda | `agendamento.proposta_criada`, `agendamento.aprovado`, `agendamento.recusado`, `agendamento.status_alterado`, `agendamento.reagendado`, `agendamento.pagamento_final_registrado`, `agendamento.conflito` |
| E-commerce | `ecommerce.checkout_criado`, `ecommerce.comprovante_enviado`, `ecommerce.pagamento_confirmado`, `ecommerce.compra_cancelada`, `ecommerce.token_galeria_regerado` |
| Financeiro | `financeiro.pagamento_registrado`, `financeiro.pagamento_extra_registrado`, `financeiro.pagamento_duplicado`, `financeiro.compra_extra_confirmada` |
| Cliente | `cliente.criado`, `cliente.atualizado`, `cliente.deletado`, `cliente.registrado`, `cliente.login`, `cliente.perfil_atualizado` |
| Fotos | `foto.upload_concluido`, `foto.publicadas`, `foto.deletada`, `foto.compra_*`, `foto.download_registrado`, `foto.imagem_invalida`, `foto.marca_dagua_falhou` |
| Comissão | `comissao.indicacao_criada`, `comissao.pagas`, `comissao.canceladas` |
| Documento | `documento.geracao_iniciada`, `documento.gerado` |
| Segurança | `security.csrf_invalido`, `security.rate_limit_excedido`, `auth.refresh_token_reuso` |
| Outros | `bloqueio.*`, `pacote.*`, `config.*`, `despesa.recorrentes_geradas`, `notificacao.*`, `fotografo.*`, `indicador.*` |

Catálogo completo em [`LOGGING.md`](./LOGGING.md) §7.1.

## 8. Queries prontas

### ELK / Kibana

```text
correlationId:"5de952bc-b63b-49a9-8740-971f46b82203"
userId:"9a3ebda5-4770-4b14-80bb-57814e0e0052"
traceId:"6add1fd7ee34bf20ee7895e605589e42"
event_action:"agendamento.*"
http_status >= 500
level:ERROR
duration_ms > 1000
http_route:"/api/v1/agenda" and http_status >= 400
```

### Datadog

```text
@correlationId:5de952bc-b63b-49a9-8740-971f46b82203
@userId:9a3ebda5-4770-4b14-80bb-57814e0e0052
@traceId:6add1fd7ee34bf20ee7895e605589e42
@http_status:>=500
@duration_ms:>1000
@event_action:agendamento.*
```

### Local (JSON)

```bash
# tudo da requisição
jq 'select(.correlationId=="5de952bc-b63b-49a9-8740-971f46b82203")' logs/crm.json

# tudo do usuário
jq 'select(.userId=="9a3ebda5-4770-4b14-80bb-57814e0e0052")' logs/crm.json

# erros e eventos, em linha
jq -r 'select(.level=="ERROR") | [."@timestamp", .correlationId, .event_action, .message] | @tsv' logs/crm.json

# requests lentos
jq 'select(.duration_ms > 1000)' logs/crm.json
```

### Console (dev)

```bash
grep '5de952bc-b63b-49a9-8740-971f46b82203' app.log
grep 'user=9a3ebda5-4770-4b14-80bb-57814e0e0052' app.log
grep 'trace=6add1fd7ee34bf20ee7895e605589e42' app.log
```

## 9. Limitações e armadilhas

- **`correlationId` não é identidade/auditoria.** Pode vir do cliente e é
  sanitizado, mas continua controlável. Para auditoria, use `userId` +
  `createdBy`/`AuditInfo` + timestamp.
- **`userId` é UUID interno**, não e-mail/CPF. Resolva o nome no banco; não espere
  PII nos logs.
- **`traceId` pode ficar vazio** em:
  - startup/seeders;
  - jobs agendados (têm `correlationId` `job-*`, mas não span);
  - threads assíncronas (hoje não há `@Async`; se houver, usar `TaskDecorator`).
- **401 é logado em DEBUG** (`SecurityErrorHandler`) para não poluir; 403 é WARN.
- **`/actuator/health`** não gera access log.
- **Sampling:** `TRACING_SAMPLING_PROBABILITY` (prod default `0.1`) afeta a
  exportação para o APM; o `correlationId` e os logs continuam sempre presentes.
  Se o trace não estiver no APM, aumente o sampling temporariamente.
- **PII mascarada:** CPF/CNPJ, e-mail, telefone, cartão, tokens e valores de
  configuração não aparecem. Use os IDs e consulte o banco.
- **4xx em WARN** pode ser comportamento esperado; não trate todo WARN como
  incidente. Alerte por taxa/limiar, não por linha isolada.

## 10. Template de bug report

```text
Título:
Ambiente: dev | homolog | prod
Data/hora (com timezone):
Usuário (userId, se staff):
Rota/método:
Status HTTP:
correlationId:
traceId (se disponível):
Passos para reproduzir:
Resultado esperado:
Resultado atual:
Evidências: print do toast/DevTools, response body (com correlationId)
```

Se o usuário não souber o `correlationId`, o suporte pode localizar pelo horário +
`userId` + rota no access log.

## 11. Processo, dashboards e alertas

1. **Frontend:** exibir `correlationId` no toast de erro com botão “copiar”;
   enviar no chamado.
2. **Dashboards:**
   - requisições por `http_route` e `http_status`;
   - erros por `event_action`;
   - p95/p99 de `duration_ms` por rota;
   - eventos de segurança (`security.csrf_invalido`,
     `security.rate_limit_excedido`, `auth.refresh_token_reuso`).
3. **Alertas:**
   - taxa de 5xx acima do baseline;
   - p95 de latência acima do SLA;
   - picos de 429;
   - reuso de refresh token (possível ataque).
4. **Revisão:** a cada fluxo novo, adicionar `event_action` e IDs de negócio;
   revisar PII; atualizar este runbook.
5. **Retenção/LGPD:** logs são dado pessoal; acesso restrito, retenção definida
   (30 dias no arquivo) e criptografia em repouso.

## 12. Referências

- [`LOGGING.md`](./LOGGING.md) — arquitetura, MDC, níveis, catálogo de eventos,
  PII e operação.
- `shared/logging/CorrelationIdFilter.java` — geração/validação do `X-Correlation-ID`.
- `shared/logging/UserMdcFilter.java` — `userId`/`userRole` no MDC.
- `shared/logging/RequestLoggingFilter.java` — access log.
- `shared/exception/GlobalExceptionHandler.java` — política de níveis e stack trace.
- `shared/exception/SecurityErrorHandler.java` — 401/403/CSRF.
- `application-prod.properties` — `TRACING_SAMPLING_PROBABILITY`.
