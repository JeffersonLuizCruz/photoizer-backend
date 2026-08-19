# Módulo: Shared

## 1. Responsabilidade
Módulo de infraestrutura compartilhada entre todos os módulos. Fornece classe base de entidade, tratamento global de exceções, configurações de infraestrutura, logging via AOP, rate limiting e armazenamento de arquivos.

## 2. Estrutura
```
shared/
├── model/
│   ├── BaseEntity.java               # @MappedSuperclass: id (UUID), createdAt, updatedAt, createdBy + @PrePersist/@PreUpdate
│   ├── FormaPagamento.java           # Enum: PIX, CARTAO, DINHEIRO, TRANSFERENCIA, OUTRO
│   └── TipoRepasse.java              # Enum: FIXO, PERCENTUAL
├── api/
│   └── PageResponse.java             # Record genérico: data, total, page, perPage, totalPages
├── config/
│   ├── CorsConfig.java               # CORS filter (localhost:5173)
│   ├── DataSeeder.java               # CommandLineRunner: usuários, configurações, categorias de despesa, backfill
│   ├── LoggingConfig.java            # @EnableAspectJAutoProxy(proxyTargetClass=true)
│   ├── OpenApiConfig.java            # Configuração SpringDoc OpenAPI/Swagger
│   └── RateLimitFilter.java          # Rate limiting em memória para endpoints da galeria pública
├── exception/
│   ├── ErrorResponse.java            # Record: status, error, message, timestamp, fieldErrors (NON_NULL)
│   └── GlobalExceptionHandler.java   # @RestControllerAdvice: mapeia ~17 exceções de domínio + genéricas
├── logging/
│   ├── LoggingAspect.java            # @Around em controllers (INFO), services (DEBUG), repositories (TRACE)
│   └── SensitiveDataMask.java        # Mascara CPF, telefone, email em logs
└── storage/
    ├── FileStorageService.java       # Interface: salvar, salvarEmSubdiretorio, deletar, getUploadDir
    └── LocalFileStorageService.java  # Implementação: grava em uploads/ (caminho absoluto)
```

## 3. Dependências Externas

### Módulos internos importados — **[VIOLAÇÕES Modulith]**
O AGENTS.md afirma que `shared` é fundacional e não importa módulos de negócio — **isso está incorreto**:
- `GlobalExceptionHandler.java:4-21` importa exceções de `agenda`, `pacote`, `cliente`, `comissao`, `edicao`, `ecommerce`, `contrato`. Dependência invertida: **infraestrutura depende do domínio**.
- `DataSeeder.java:3-14` importa `auth`, `config`, `despesa`, `indicador`. O seeder cruza módulos para semear dados.

### Módulos que dependem deste
Todos: `BaseEntity` (26 entidades), `FileStorageService` (agenda, edicao, ecommerce, foto), `GlobalExceptionHandler`, `PageResponse`, `FormaPagamento`/`TipoRepasse` (agenda, financeiro), `CorsConfig` (auth), `LoggingAspect`/`SensitiveDataMask`.

## 4. Componentes

### BaseEntity
- `@MappedSuperclass` + Lombok `@SuperBuilder`, `@Getter/@Setter`, `@NoArgsConstructor(protected)`.
- Campos: `id` (UUID, `@GeneratedValue(GenerationType.UUID)`), `createdAt`, `updatedAt`, `createdBy` (String).
- `@PrePersist`/`@PreUpdate` manuais para timestamps (`BaseEntity.java:40-49`).

### GlobalExceptionHandler
- 17 handlers de exceções de domínio mapeando para status HTTP (404/409/422/410/401).
- 6 handlers genéricos: validação (422), upload (413), `IllegalArgumentException` (422), parte ausente (422), acesso negado (403), genérica (500).
- `ErrorResponse` com `@JsonInclude(NON_NULL)` — campos nulos omitidos.

### LoggingAspect
- AOP intercepta: controllers (INFO, method+path+duração), services (DEBUG, args mascarados+resultado), repositories (TRACE).
- `getResultSummary` resume resultados por tipo (`LoggingAspect.java:123-141`).

### RateLimitFilter
- Janela fixa de 60s por IP+path em memória (`ConcurrentHashMap`) — `RateLimitFilter.java:32`.
- Limites: `/download-zip` 5, `/checkout` 10, `/comprovante` 10, `/selecionar` 60, `/sessao` 30.
- Aplica-se apenas a paths contendo `/ecommerce/galeria/` ou `/ecommerce/sessao` (`shouldNotFilter`).

### DataSeeder
- Semearia 5 usuários, 5+6 configurações, 11 categorias de despesa, backfill de despesas legadas e limpeza de indicadores duplicados via `EntityManager` (HQL `GROUP BY ... HAVING COUNT > 1`).
- Template de contrato em texto (`TEMPLATO_PADRAO`) com placeholders `{{...}}`.

## 5. Regras Específicas
1. **`BaseEntity` gera UUID via Hibernate** (`GenerationType.UUID`). Entidades fora da herança (`User`, `Notificacao`, `Configuracao`) gerenciam ID manualmente — inconsistência.
2. **`CorsConfig` regista 2 beans quase idênticos** (`corsFilter` e `corsConfigurationSource`), ambos com mesmo `corsConfig()`.
3. **Rate limit é em memória**: resetado a cada restart; não funciona com múltiplas instâncias; sujeito a estouro de memória no `ConcurrentHashMap` (sem eviction de janelas antigas).
4. **`SensitiveDataMask` máscara por regex** após truncar strings > 500 chars.

## 6. Testes
Nenhum teste específico para este módulo. Apenas `CrmApplicationTests` (smoke de contexto).

## 7. Dívidas Técnicas e Melhorias Recomendadas

### 7.1 Herança → Composição (`BaseEntity`) — **P1**
- **Problema**: 26 entidades estendem `@MappedSuperclass` (`BaseEntity.java`). O usuário do projeto não quer herança; `@SuperBuilder` só existe para suportar a cadeia de herança; campos de auditoria são intrinsecamente composição.
- **Solução**: substituir por **composição** via `@Embeddable` + Spring Data JPA Auditing:
  1. Criar `@Embeddable AuditInfo` (createdAt, updatedAt, createdBy) e um `@Embeddable EntityId`? Melhor: manter `id` via `@Embeddable` não resolve `@GeneratedValue`; alternativa limpa é:
     - `@Embeddable AuditInfo` composto em cada entidade (sem herança) + `@EntityListeners(AuditingEntityListener.class)` global via `@EnableJpaAuditing`.
     - `id` UUID: ou repetir o campo com `@GeneratedValue(UUID)` em cada entidade, ou criar componente JPA de configuração de ID. Recomendação: `@Embeddable AuditInfo` (composição de verdade) + anotação de auditoria; eliminar `BaseEntity`, `@SuperBuilder` e os `@PrePersist/@PreUpdate` manuais.
  2. Resultado: entidades deixam de herdar; lombok passa a usar `@Builder` (em vez de `@SuperBuilder`); auditoria delegada ao Spring.
- Benefício secundário: `equals`/`hashCode` das entidades podem ser definidos pelo `id` sem herança.

### 7.2 Dependência invertida `shared → módulos` — **P1**
- `GlobalExceptionHandler.java:4-28` e `DataSeeder.java:3-14` importam módulos de negócio. Infraestrutura depende do domínio, quebrando o ciclo recomendado do Modulith.
- **Solução**:
  - Para exceções: criar hierarquia central em `shared` (`BusinessException` com `HttpStatus` + código), eliminar os ~17 handlers específicos (ver 7.3).
  - Para o seeder: mover `DataSeeder` para o módulo `config` (que já é dono do `Configuracao`) e/ou criar eventos `DadosSemeadosEvent`; o seeder não deveria conhecer `auth`/`despesa`/`indicador` internamente.

### 7.3 Hierarquia de exceções e `GlobalExceptionHandler` — **P1**
- **Problema**: 18 classes de exceção quase idênticas em 7 módulos + 17 métodos boilerplate no handler (`GlobalExceptionHandler.java:40-146`).
- **Solução** (aprovada pelo usuário — "Hierarquia central"):
  1. Criar em `shared/exception`:
     - `BusinessException extends RuntimeException` com `HttpStatus status` (default 4xx/5xx) e opcional `ErrorCode` enum.
     - Subclasses marcadoras: `NotFoundException`, `ConflictException`, `UnprocessableEntityException`, `UnauthorizedException`, `GoneException`.
  2. Cada módulo troca suas classes por uma dessas subclasses (ou usa `new NotFoundException("Cliente não encontrado: " + id)`).
  3. `GlobalExceptionHandler` reduz para ~5 handlers (`BusinessException`, validação, upload, acesso negado, genérica).

### 7.4 `ErrorResponse` sem suporte a múltiplos erros e sem código — **P1**
- `ErrorResponse.java` tem `fieldErrors` mas nenhum campo de código de erro de negócio; `timestamp` é `LocalDateTime` (não ISO-8601 UTC).
- **Solução**: adicionar `code` (String) opcional ao record; usar `Instant`/`OffsetDateTime` em UTC para consistência entre nós.

### 7.5 RateLimitFilter — falta eviction e escala — **P2**
- `ConcurrentHashMap` (`RateLimitFilter.java:32`) cresce sem limite (1 entry por IP+path); janelas antigas nunca são limpas.
- **Solução**: `Caffeine cache` com `expireAfterWrite(WINDOW_MS)` (já é uma lib padrão de cache), ou `cleanup()` periódico; parametrizar limites via `application.properties`.

### 7.6 `CorsConfig` duplicação de beans — **P3**
- `CorsConfig.java:16-26` define `corsFilter` e `corsConfigurationSource` com o mesmo `corsConfig()`. Manter apenas o `CorsConfigurationSource` + `@EnableWebMvc`/`SecurityConfig` o consome; origens e métodos deveriam vir de configuração por profile.

### 7.7 `LocalFileStorageService` — sem validação de extensão e caminho — **P2**
- `salvarEmSubdiretorio` extrai extensão do nome original (`LocalFileStorageService.java:59-64`) sem saneamento; `salvar` (`:29-44`) aceita qualquer tipo; nomes originais podem conter path traversal.
- **Solução**: whitelist de extensões por tipo (imagem/pdf/zip), sanitizar nome de arquivo, retornar caminho relativo segurto para o banco, e validar na camada de serviço.

### 7.8 `DataSeeder` — responsabilidades demais e não-idempotência parcial — **P2**
- Seeda usuários, configurações, categorias, backfill de despesas, limpeza de duplicados e template de contrato — 6 responsabilidades em um único arquivo (`DataSeeder.java`).
- `limparIndicadoresDuplicados` deleta em loop (N+1 deletes); `backfillDespesasLegadas` usa JPQL em `EntityManager`.
- **Solução**: separar em `Seeders` por módulo (via eventos ou profiles); usar `@ConditionalOnProperty`/profile `dev`; remover lógica de limpeza de produção.

### 7.9 Lombok — exposição de setters em entidades — **P2**
- Todas as entidades usam `@Setter` de classe, permitindo mutação arbitrária fora de invariantes.
- **Solução**: usar `@Setter(AccessLevel.PRIVATE)` + métodos de domínio que validam transições de estado; `@Builder` para construção; `@Getter` para leitura.

### 7.10 Ausência de testes e auditoria não auditada — **P1**
- `createdBy` nunca é preenchido (nenhum código seta), e não há `@CreatedBy`/`AuditorAware` com o usuário do JWT.
- **Solução**: ativar `@EnableJpaAuditing` + `AuditorAware` lendo o `SecurityContext`.

## 8. Exemplos de arquivos afetados
- `BaseEntity.java:24-49` — herança a remover; `GlobalExceptionHandler.java:40-146` — handlers a consolidar; `DataSeeder.java:58-94` — seeder cross-module; `RateLimitFilter.java:32` — cache sem eviction; `LocalFileStorageService.java:29-74` — salvamento sem validação; `CorsConfig.java:16-26` — beans duplicados.