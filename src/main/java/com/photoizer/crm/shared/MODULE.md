# Módulo: Shared

## 1. Responsabilidade
Módulo de infraestrutura compartilhada entre todos os módulos. Fornece composição de auditoria, hierarquia de exceções, tratamento global de erros, configurações de infraestrutura, logging via AOP, rate limiting, armazenamento de arquivos, geração de PDF, processamento de imagens e portas para cálculos financeiros.

## 2. Estrutura
```
shared/
├── api/
│   └── PageResponse.java             # Record genérico: data, total, page, perPage, totalPages
├── auth/
│   └── TokenService.java             # Interface para geração de tokens (Dependency Inversion)
├── config/
│   ├── CorsConfig.java               # CORS filter (localhost:5173)
│   ├── LoggingConfig.java            # @EnableAspectJAutoProxy(proxyTargetClass=true)
│   ├── OpenApiConfig.java            # Configuração SpringDoc OpenAPI/Swagger
│   ├── RateLimitFilter.java          # Rate limiting com Caffeine cache para galeria pública
│   └── RateLimitProperties.java      # @ConfigurationProperties: window-ms, maximum-size, limits
├── exception/
│   ├── ErrorCode.java                # Enum: códigos de erro de negócio (~60 constantes)
│   ├── BusinessException.java        # Base: HttpStatus + ErrorCode
│   ├── NotFoundException.java        # 404
│   ├── BadRequestException.java      # 400
│   ├── ConflictException.java        # 409
│   ├── UnprocessableException.java   # 422
│   ├── GoneException.java            # 410
│   ├── UnauthorizedException.java    # 401
│   ├── ForbiddenException.java       # 403
│   ├── ErrorResponse.java            # Record: status, error, message, code, timestamp(UTC), fieldErrors
│   └── GlobalExceptionHandler.java   # @RestControllerAdvice: 7 handlers de hierarquia + genéricos Spring
├── logging/
│   ├── LoggingAspect.java            # @Around em controllers (INFO), services (DEBUG), repositories (TRACE)
│   └── SensitiveDataMask.java        # Mascara CPF, telefone, email em logs
├── model/
│   ├── AuditInfo.java                # @Embeddable: createdAt, updatedAt, createdBy (composição > herança)
│   ├── AuditInfoListener.java        # JPA EntityListener: @PrePersist/@PreUpdate + createdBy via SecurityContext
│   ├── FormaPagamento.java           # Enum: PIX, CARTAO, DINHEIRO, TRANSFERENCIA, OUTRO
│   └── TipoRepasse.java              # Enum: FIXO, PERCENTUAL
├── pdf/
│   └── PdfWriter.java                # Facade: geração de PDF via OpenPDF
├── port/
│   ├── StatusClassificationPort.java # Porta: classificação de status de agendamentos (lógica pura)
│   ├── DisplacementCostPort.java     # Porta: cálculo de custo de deslocamento (lógica pura)
│   └── RepasseAggregationPort.java   # Porta: agregação de repasses (I/O via adaptador no agenda)
├── processing/
│   └── ImageProcessingService.java   # Thumbnail + watermark via Thumbnailator + AWT
├── service/
│   ├── StatusClassificationAdapter.java  # Adaptador: lógica de classificação de status
│   └── DisplacementCostAdapter.java      # Adaptador: regra de deslocamento efetivo
└── storage/
    ├── FileServeHelper.java          # Helper: servir arquivos via HTTP com validação de segurança
    ├── FileStorageService.java       # Interface: salvar, salvarEmSubdiretorio, deletar, getUploadDir
    ├── FileValidator.java            # Validator: whitelist de extensões, sanitização, path traversal
    └── LocalFileStorageService.java  # Implementação: grava em uploads/ com validação de segurança
```

## 3. Dependências Externas

### Módulos internos importados — **[TODAS RESOLVIDAS]**
- `GlobalExceptionHandler`: zero imports de domínio.
- `FinanceCalculator`: **deletado** — substituído por portas em `shared/port/` + adaptadores em `shared/service/` e `agenda/adapter/`.
- `DataSeeder`: **deletado** — decomposto em 5 seeders por módulo (`auth/seed/`, `config/seed/`, `indicador/seed/`, `despesa/seed/`).

### Módulos que dependem deste
Todos: `AuditInfo` (25 entidades), `FileStorageService` (agenda, edicao, ecommerce, foto), `FileValidator` (agenda, edicao, ecommerce, despesa), `GlobalExceptionHandler`, `PageResponse`, `FormaPagamento`/`TipoRepasse` (agenda, financeiro), `CorsConfig` (auth), `LoggingAspect`/`SensitiveDataMask`, hierarquia `BusinessException` (todos), portas financeiras (`StatusClassificationPort`, `DisplacementCostPort`, `RepasseAggregationPort` — dashboard, financeiro, agenda).

## 4. Componentes

### Hierarquia de Exceções
- `BusinessException extends RuntimeException` com `HttpStatus` + `ErrorCode` enum.
- 7 subclasses marcadoras: `NotFoundException`(404), `BadRequestException`(400), `ConflictException`(409), `UnprocessableException`(422), `GoneException`(410), `UnauthorizedException`(401), `ForbiddenException`(403).
- 61 exceções de domínio em 14 módulos migradas para extender as subclasses.
- `GlobalExceptionHandler`: 7 handlers genéricos + handlers Spring → zero imports de domínio.

### AuditInfo
- `@Embeddable` com `createdAt`, `updatedAt`, `createdBy`. Composição em vez de herança.
- `AuditInfoListener` popula `createdBy` via `SecurityContextHolder`. Fallback `"SYSTEM"`.

### Portas Financeiras (Ports & Adapters)
- `StatusClassificationPort`: classificação de status (pura)
- `DisplacementCostPort`: cálculo de deslocamento (pura)
- `RepasseAggregationPort`: agregação de repasses (I/O)

### RateLimitFilter
- Caffeine cache com `expireAfterWrite` + `maximumSize`.
- Config externa via `RateLimitProperties` (`app.rate-limit.*`).

### FileValidator
- Whitelist de extensões fixa por contexto (image, raw, edited, receipt, any).
- Sanitização de filename e validação de path traversal.

### LoggingAspect
- AOP: controllers (INFO), services (DEBUG), repositories (TRACE).

### PdfWriter
- Facade: geração de PDF via OpenPDF.

## 5. Regras Específicas
1. **`AuditInfo` composto** em todas as 25 entidades. `createdBy` populado automaticamente via SecurityContext.
2. **`CorsConfig`** regista 2 beans (pendente simplificação P3).
3. **`FileValidator`** whitelist fixa no código — não parametrizável via properties.
4. **`IndicadorCleanupSeeder`** roda apenas em `@Profile("!prod")`.

## 6. Testes
`CrmApplicationTests` (smoke de contexto). Testes unitários em módulos consumidores.

## 7. Dívidas Restantes

### 7.1 `CorsConfig` duplicação de beans — **P3**
- Dois beans com a mesma configuração. Origens hardcoded.

### 7.2 `SensitiveDataMask` — mascaramento pós-truncamento — **P3**
- Strings > 500 chars truncadas antes de mascarar.

## 8. Exemplos de arquivos afetados nesta refatoração
- `GlobalExceptionHandler.java` — 509→~130 linhas, zero imports de domínio
- `ErrorResponse.java` — campo `code` + `OffsetDateTime`
- `AuditInfo.java` — campo `createdBy`
- `AuditInfoListener.java` — `SecurityContextHolder` para createdBy
- `LocalFileStorageService.java` — path traversal fix + FileValidator
- `RateLimitFilter.java` — Caffeine + config externa
- 61 arquivos de exceção em 14 módulos — migrados para hierarquia
- 9 arquivos novos em `shared/exception/` — hierarquia de exceções
- 3 arquivos novos em `shared/port/` — portas financeiras
- 2 arquivos novos em `shared/service/` — adaptadores puros
- 1 arquivo novo em `agenda/adapter/` — adaptador de repasses
- 1 arquivo novo em `shared/storage/FileValidator.java`
- 1 arquivo novo em `shared/config/RateLimitProperties.java`
- 5 arquivos novos de seeders em módulos (auth, config, indicador, despesa)
- `DataSeeder.java` — deletado
- `FinanceCalculator.java` — deletado
