# Logging e Observabilidade — CRM Photoizer (backend)

> Documento operacional e de convenção. Foi versionado com `git add -f` apesar
> da regra `*.md` do `.gitignore`.
>
> Runbook de diagnóstico: [`DEBUGGING.md`](./DEBUGGING.md) — como usar
> `correlationId`, `userId` e `traceId` para resolver bugs.

## 1. Visão geral

Toda requisição HTTP produz:

1. **uma linha de access log** (`INFO`) com método, path, status e latência;
2. **logs de serviço** (`DEBUG`) com argumentos mascarados e duração;
3. **logs de negócio** (`INFO`/`WARN`/`ERROR`) escritos explicitamente nos services;
4. **logs de erro** no `GlobalExceptionHandler` (uma única vez, com stack trace em 5xx).

Todos carregam o mesmo `correlationId` e, quando autenticado, `userId` e `userRole`.

```
Request
  │
  ├─ CorrelationIdFilter        @Order(HIGHEST_PRECEDENCE)
  │    ├─ reutiliza X-Correlation-ID válido ou gera UUID
  │    ├─ MDC.put("correlationId")
  │    └─ response.setHeader("X-Correlation-ID")
  │
  ├─ RequestLoggingFilter       @Order(HIGHEST_PRECEDENCE + 10)
  │    └─ 1 linha INFO por request (status real, inclusive 401/403/429)
  │
  ├─ Spring Security
  │    ├─ JwtAuthenticationFilter
  │    └─ UserMdcFilter (após o JWT) → MDC userId/userRole
  │
  ├─ Controller (sem log manual)
  │    └─ Service (LoggingAspect em DEBUG)
  │
  ├─ GlobalExceptionHandler
  │    ├─ 4xx → WARN, sem stack
  │    └─ 5xx → ERROR, com stack
  │
  └─ Logback
       ├─ dev/test: console colorido
       └─ homolog/prod: JSON (LogstashEncoder) + arquivo assíncrono em prod
```

## 2. Componentes

| Classe | Responsabilidade |
|---|---|
| `shared/logging/LoggingKeys` | constantes canônicas do MDC (`correlationId`, `userId`, `userRole`, `tenantId`) |
| `shared/logging/CorrelationIdFilter` | gera/reutiliza o `X-Correlation-ID`, publica no MDC, devolve no header e limpa ao final |
| `shared/logging/UserMdcFilter` | publica `userId` (UUID interno) e `userRole` após o JWT |
| `shared/logging/RequestLoggingFilter` | access log HTTP com status real e latência |
| `shared/logging/LoggingAspect` | DEBUG de services (args mascarados/duração) e TRACE de repositories |
| `shared/logging/SensitiveDataMask` | mascaramento de PII (CPF, CNPJ, e-mail, telefone, cartão, JWT, chaves sensíveis) |
| `shared/exception/GlobalExceptionHandler` | contrato de erro + política de níveis |
| `shared/exception/SecurityErrorHandler` | 401/403 no contrato `ErrorResponse` (inclusive CSRF) |

## 3. Campos do MDC

| Chave | Origem | Exemplo |
|---|---|---|
| `correlationId` | `CorrelationIdFilter` (header ou UUID) | `9f1c2d3e-4a5b-6c7d-8e9f-0a1b2c3d4e5f` |
| `userId` | `UserMdcFilter` (subject String do JWT, convertido para UUID) | `b0e1c2d3-...` |
| `userRole` | `UserMdcFilter` (authorities) | `ROLE_ADMIN,ROLE_AGENDADOR` |
| `tenantId` | reservado (multi-tenant futuro) | — |
| `traceId`/`spanId` | Micrometer/Brave (requer `spring-boot-micrometer-tracing-brave`) | `5c2a...` |

> **Nunca** coloque e-mail, CPF, telefone ou username no MDC. Use os UUIDs internos.

## 4. Formato do log

### 4.1 Desenvolvimento (`!homolog & !prod`)

Console colorido, legível:

```
2026-10-24 16:23:37.097 WARN  c.p.c.s.e.GlobalExceptionHandler [chamado-123] [user=] [trace=] - [404] Recurso não encontrado
2026-10-24 16:23:37.118 INFO  c.p.c.s.logging.RequestLoggingFilter [chamado-123] [user=] [trace=] - GET /api/v1/rota-inexistente -> 404 (34 ms)
```

### 4.2 Homolog/prod (JSON)

```json
{
  "@timestamp": "2026-10-24T19:23:37.118Z",
  "message": "GET /api/v1/rota-inexistente -> 404 (34 ms)",
  "logger": "com.photoizer.crm.shared.logging.RequestLoggingFilter",
  "level": "INFO",
  "service": "crm",
  "env": "prod",
  "version": "1.4.2",
  "correlationId": "chamado-123",
  "userId": "b0e1c2d3-...",
  "userRole": "ROLE_ADMIN",
  "traceId": "5c2a...",
  "spanId": "9d1f...",
  "http_method": "GET",
  "http_path": "/api/v1/rota-inexistente",
  "http_status": 404,
  "duration_ms": 34,
  "http_route": "/api/v1/**"
}
```

`addKeyValue(...)` vira campo de topo no JSON. Campos de exceção vão em
`error.stack_trace`.

## 5. Política de níveis

| Situação | Nível | Stack trace? |
|---|---|---|
| Access log HTTP (1 linha) | INFO | não |
| Marco de negócio (proposta criada, pagamento confirmado, fotos publicadas) | INFO + `event_action` | não |
| Regra de negócio esperada (404/409/422) | WARN | não |
| Segurança (401/403/429, CSRF, token revogado) | WARN | não |
| Falha técnica (I/O, PDF, banco, integração) | ERROR | sim (uma vez) |
| Detalhe de service/payload sob opt-in | DEBUG | não |
| SQL/detalhe técnico | TRACE | não |

**Nunca** logue 4xx como ERROR. **Nunca** logue o mesmo erro no service e no advice.

## 6. PII — regras de ouro

1. Não logue body de requisição por padrão (multipart chega a 200 MB e há LGPD).
2. Não logue query string: use apenas `request.getRequestURI()`.
3. Não logue senha, token, JWT, cartão, CPF, e-mail ou telefone.
4. Use IDs internos (`cliente_id`, `agendamento_id`, `compra_id`).
5. Se precisar logar um valor sensível, passe por `SensitiveDataMask.mask(...)`.
6. Prefira `addKeyValue` a concatenação de strings.
7. `SensitiveDataMask` é best-effort; nomes/endereços completos não são detectáveis.
8. O `MaskingJsonGeneratorDecorator` do Logback mascara campos JSON chamados
   `cpf`, `email`, `telefone`, `senha`, `token`, `authorization` (defesa em profundidade).
9. Em dev, o console é texto: **não** há decorator; use o utilitário explicitamente.

## 7. Como logar nos services

```java
// INFO — marco de negócio (auditoria)
log.atInfo()
   .addKeyValue("event_action", "agendamento.proposta_criada")
   .addKeyValue("agendamento_id", agendamento.getId())
   .addKeyValue("pacote_id", pacote.getId())
   .addKeyValue("valor_total", valores.total())
   .log("Proposta de agendamento criada");

// WARN — regra esperada, sem stack trace
log.atWarn()
   .addKeyValue("event_action", "agendamento.conflito")
   .addKeyValue("agendamento_id", command.agendamentoId())
   .addKeyValue("fotografo_id", fotografoId)
   .log("Conflito de agenda ao confirmar proposta");

// ERROR — falha técnica (o stack trace vai no JSON)
log.atError()
   .addKeyValue("event_action", "documento.geracao_falhou")
   .addKeyValue("agendamento_id", agendamentoId)
   .setCause(e)
   .log("Falha ao gerar o contrato em PDF");

// DEBUG — payload sob opt-in e mascarado
if (log.isDebugEnabled()) {
    log.atDebug()
       .addKeyValue("cliente_id", clienteId)
       .addKeyValue("payload", SensitiveDataMask.mask(request))
       .log("Atualização de cliente recebida");
}
```

> **Tipos nos `addKeyValue`:** use `String`, `UUID`, `BigDecimal`, enums, `Integer`,
> `Long` e `Boolean`. `LocalDateTime`/`Instant`/`Path` devem ser convertidos com
> `.toString()` (o encoder JSON não registra o `JavaTimeModule` por padrão).

### 7.1 Catálogo de eventos (`event_action`)

| Módulo | `event_action` | Nível |
|---|---|---|
| agenda | `agendamento.proposta_criada`, `agendamento.aprovado`, `agendamento.recusado`, `agendamento.proposta_removida` | INFO |
| agenda | `agendamento.status_alterado`, `agendamento.reagendado`, `agendamento.pagamento_final_registrado` | INFO |
| agenda | `agendamento.token_galeria_regenerado` (sem o token) | INFO |
| auth | `auth.refresh_token_reuso` | WARN |
| auth | `auth.tokens_expirados_purgados`, `auth.parceiro_legado_*` | INFO/WARN/ERROR |
| cliente | `cliente.criado`, `cliente.atualizado`, `cliente.deletado`, `cliente.registrado`, `cliente.login`, `cliente.perfil_atualizado` | INFO |
| ecommerce | `ecommerce.checkout_criado`, `ecommerce.comprovante_enviado`, `ecommerce.pagamento_confirmado`, `ecommerce.compra_cancelada`, `ecommerce.token_galeria_regerado` | INFO |
| ecommerce | `ecommerce.fotos_selecionadas`, `ecommerce.link_pagamento_atualizado` | INFO |
| financeiro | `financeiro.pagamento_registrado`, `financeiro.pagamento_extra_registrado`, `financeiro.compra_extra_confirmada` | INFO |
| financeiro | `financeiro.pagamento_duplicado`, `financeiro.pagamento_extra_duplicado` | WARN |
| comissao | `comissao.indicacao_criada`, `comissao.pagas`, `comissao.canceladas` | INFO |
| foto | `foto.upload_concluido`, `foto.publicadas`, `foto.deletada`, `foto.compra_*`, `foto.download_registrado` | INFO |
| foto | `foto.imagem_invalida`, `foto.thumbnail_falhou`, `foto.delecao_arquivo_falhou` | WARN |
| foto | `foto.marca_dagua_falhou` | ERROR |
| documento | `documento.geracao_iniciada`, `documento.gerado` | INFO |
| bloqueio | `bloqueio.criado`, `bloqueio.atualizado`, `bloqueio.cancelado` | INFO |
| pacote | `pacote.criado`, `pacote.atualizado`, `pacote.desativado` | INFO |
| config | `config.atualizada`, `config.atualizadas`, `config.valor_invalido` | INFO/WARN |
| despesa | `despesa.recorrentes_geradas` (job) | INFO |
| notificacao | `notificacao.criada`, `notificacao.reatribuicao` | INFO |
| fotografo | `fotografo.provisionado`, `fotografo.reconciliado`, `fotografo.*_falhou` | INFO/WARN |
| indicador | `indicador.duplicados_removidos` | WARN |
| shared | `security.csrf_invalido`, `security.rate_limit_excedido` | WARN |

> `dashboard` é somente leitura: não tem log de negócio próprio; o access log cobre
> todas as suas requisições. Se necessário, adicione métricas de latência depois.


### Contexto de negócio no MDC (fluxos longos)

```java
try (var ignored = MDC.putCloseable("agendamento_id", agendamentoId.toString())) {
    // todos os logs daqui (inclusive o do GlobalExceptionHandler) levam o id
}
```

## 8. Correlação com o frontend e suporte

- O backend devolve `X-Correlation-ID` em **toda** resposta (inclusive 401/403/429/500).
- O corpo de erro traz `correlationId`.
- O CORS expõe o header (`Access-Control-Expose-Headers`), então o frontend pode
  lê-lo e mostrar ao usuário: “Informe este código ao suporte: ...”.
- Consulta no ELK: `correlationId:"<valor>"`. No Datadog: `@correlationId:<valor>`.
- Consulta de erros: `level:ERROR` ou `@http_status:>=500`.
- Latência: agregar `@duration_ms` por `@http_route`.

## 9. Operação / LGPD

- **Retenção:** 30 dias no arquivo (`maxHistory=30`); replicar a política no coletor.
- **Acesso:** logs são dado pessoal sob LGPD; restrinja quem lê e criptografe em repouso.
- **Stdout:** em containers, prefira stdout (12-factor); o arquivo em prod é opcional.
- **Sampling:** em prod, `management.tracing.sampling.probability` é
  `${TRACING_SAMPLING_PROBABILITY:0.1}` (custo); o `correlationId` continua sempre
  presente (não depende do tracing).
- **Dependência de tracing (Spring Boot 4):** é obrigatório ter
  `org.springframework.boot:spring-boot-micrometer-tracing-brave` no classpath.
  Apenas `io.micrometer:micrometer-tracing-bridge-brave` **não** ativa `Tracer`,
  spans nem `traceId`/`spanId` no MDC. O `WebMvcObservationAutoConfiguration`
  registra o `ServerHttpObservationFilter` em `MIN_VALUE + 1`, antes do
  `RequestLoggingFilter` (`MIN_VALUE + 10`), então o access log enxerga o traceId.
- **Principal:** o `JwtAuthenticationFilter` mantém o subject como `String`
  (compatibilidade com `@AuthenticationPrincipal String`); o `UserMdcFilter`
  converte para UUID antes de publicar. Username/e-mail nunca vão para o MDC.
- **Cardinalidade:** o `X-Correlation-ID` do cliente é sanitizado, mas ainda controlável;
  monitore cardinalidade e não o use como identidade.
- **Async:** os `@EventListener` atuais são síncronos e herdam o MDC. Se virarem
  `@ApplicationModuleListener`/`@Async`, adicione um `TaskDecorator` que copie o MDC.
- **Jobs agendados:** gere um `correlationId` (`job-<nome>-<uuid>`) e coloque no MDC
  durante a execução (ex.: `RefreshTokenService.expurgarExpirados`).

## 10. Verificação

```bash
# testes dos componentes de logging
mvn -Dtest='CorrelationIdFilterSpec,UserMdcFilterSpec,SensitiveDataMaskSpec,RequestLoggingFilterSpec,CorrelationIdIntegrationSpec,TracingConfigurationSpec' test

# suíte completa
mvn test

# manual: header reutilizado e presente no log
curl -i -H 'X-Correlation-ID: chamado-123' http://localhost:8080/api/v1/agenda

# JSON de produção
SPRING_PROFILES_ACTIVE=prod JWT_SECRET=... SPRING_DATASOURCE_URL=... \
  java -jar target/crm-0.0.1-SNAPSHOT.jar | jq .
```

## 11. Limitações / roadmap

- **Cobertura de negócio:** todos os módulos de escrita têm `event_action` (ver §7.1).
  `dashboard` é somente leitura e fica coberto pelo access log; não tem log de negócio.
- **PII:** nomes, e-mails, telefones, CPF/CNPJ, tokens e valores de configuração
  (`PIX_CHAVE`, `CNPJ_CONTRATADA`, `ENDERECO_CONTRATADA`) não são mais logados.
  O `SensitiveDataMask` é a segunda linha de defesa.
- Payload logging opt-in com `@LogPayload` (ContentCachingRequestWrapper) e bloqueio
  para multipart/auth ainda não implementado — proposital.
- Mascaramento de nomes/endereços não é automático (regex não cobre).
- `tenantId` reservado para quando houver multi-tenant.
- Se o volume de access log crescer, avaliar sampling de 2xx e manter 100% de 4xx/5xx.
