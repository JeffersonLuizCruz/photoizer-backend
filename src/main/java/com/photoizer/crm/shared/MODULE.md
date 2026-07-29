# Módulo: Shared

## 1. Responsabilidade
Módulo de infraestrutura compartilhada entre todos os módulos. Não é um módulo de negócio — fornece classes base, configurações globais, tratamento de exceções, logging e armazenamento de arquivos.

## 2. Estrutura
```
shared/
├── model/
│   └── BaseEntity.java           # @MappedSuperclass: id (UUID), createdAt, updatedAt, createdBy + @PrePersist/@PreUpdate
├── api/
│   └── PageResponse.java         # Record genérico: data, total, page, perPage, totalPages
├── config/
│   ├── CorsConfig.java           # CORS configuration source (permissivo em dev)
│   ├── DataSeeder.java           # CommandLineRunner: popula 5 usuários + 6 configurações
│   ├── LoggingConfig.java        # Configuração de logging estruturado (Logstash)
│   └── OpenApiConfig.java        # Configuração SpringDoc OpenAPI/Swagger
├── exception/
│   ├── ErrorResponse.java        # Record: status, message, timestamp, path
│   └── GlobalExceptionHandler.java # @RestControllerAdvice: mapeia exceções → ErrorResponse + HTTP status
├── logging/
│   ├── LoggingAspect.java        # @Around: loga controllers (método + duração), services (args mascarados + resultado), repositories
│   └── SensitiveDataMask.java    # Utilitário para mascarar CPF, telefone, email em logs
└── storage/
    └── FileStorageService.java   # Interface + LocalFileStorageService: salva arquivos em uploads/{subdir}
```

## 3. Dependências Externas
Nenhuma. Módulo fundacional — não importa módulos de negócio.

### Módulos que dependem deste
Todos os módulos dependem de `shared`:
- `BaseEntity` → estendido por `Cliente`, `Agendamento`, `Pacote`, `Indicacao`, `Despesa`, `CompraExtra`, `Pedido`, `ItemCarrinho`, `Favorito`, `Edicao`, `FotoEdicao`, `FotoEnsaio`, `Indicador`, `Pagamento`, `FotoExtra`, `VideoExtra`
- `FileStorageService` → usado por `agenda`, `edicao`, `ecommerce`, `foto`
- `GlobalExceptionHandler` → tratatamento global de exceções
- `PageResponse` → usado em controllers paginados
- `CorsConfig` → usado em `SecurityConfig` (auth)
- `LoggingAspect` → AOP intercepta controllers, services, repositories

## 4. Componentes

### BaseEntity
- `@MappedSuperclass` com Lombok `@SuperBuilder`, `@Getter/@Setter`, `@NoArgsConstructor`
- Campos: `id` (UUID, gerado automaticamente), `createdAt`, `updatedAt`, `createdBy` (String opcional)
- `@PrePersist` → seta `createdAt` e `updatedAt`
- `@PreUpdate` → seta `updatedAt`

### PageResponse
- Record genérico `<T>`: `List<T> data`, `long total`, `int page`, `int perPage`, `int totalPages`
- Factory: `PageResponse.from(Page<T> page, int currentPage)`

### DataSeeder
- `CommandLineRunner` que popula dados iniciais se o banco estiver vazio:
  - **5 usuários**: admin, fotógrafo (x2), editor, agendador
  - **6 configurações**: `comissao_percentual_padrao`, `taxa_deslocamento_padrao`, `percentual_entrada_padrao`, `prazo_lembrete_ensaio_dias`, `prazo_alerta_edicao_dias`, `prazo_expiracao_token_galeria_dias`

### GlobalExceptionHandler
- `@RestControllerAdvice` com handlers para:
  - `MethodArgumentNotValidException` → 400 Bad Request
  - `ConstraintViolationException` → 400 Bad Request
  - `BadCredentialsException` → 401 Unauthorized
  - `RuntimeException` genérica → 500 Internal Server Error
  - `Exception` genérica → 500 Internal Server Error
- Retorna `ErrorResponse` (record com status, message, timestamp, path)

### LoggingAspect
- Controller: loga método + argumentos + duração (nível INFO)
- Service: loga método + argumentos mascarados + resultado + duração (nível DEBUG)
- Repository: loga método + argumentos (nível TRACE)
- Usa `SensitiveDataMask` para mascarar dados sensíveis (CPF, telefone, email) nos logs

### FileStorageService
- Interface: `salvar(String nome, MultipartFile)`, `salvarEmSubdiretorio(MultipartFile, UUID subdir, String prefixo)`, `deletar(String caminho)`, `getUploadDir()`
- Implementação: `LocalFileStorageService` salva em `uploads/` com timestamp para evitar colisão
- Uploads servidos estaticamente via `file:uploads/` configurado no application.properties

## 5. Regras Específicas
1. **Módulo sem pacote `service/`**: Diferente dos módulos de negócio, `shared` não tem services — apenas configurações e utilitários.
2. **`BaseEntity` usa `@GeneratedValue(strategy = GenerationType.UUID)`**: Geração automática de UUID pelo Hibernate. Entidades que não estendem `BaseEntity` (User, Notificacao, Configuracao) gerenciam ID manualmente.
3. **`DataSeeder` usa `@Profile("!prod")`**: Só popula dados fora do profile de produção.
4. **`GlobalExceptionHandler` não trata exceções de domínio**: Exceções específicas como `ClienteNaoEncontradoException`, `AgendamentoNaoEncontradoException` são subclasses de `RuntimeException` e caem no handler genérico (500).

## 6. Testes
Nenhum teste específico.

## 7. Pontos de Atenção
- **`GlobalExceptionHandler` trata `RuntimeException` como 500**: Exceções de domínio (ex: `ClienteNaoEncontradoException`) são `RuntimeException` e retornam 500. Idealmente, cada exceção de domínio deveria ter handler específico com status adequado (404, 400, etc.).
- **`DataSeeder` não é idempotente**: Verifica apenas se o repositório de usuários está vazio. Se houver um usuário mas não houver configurações, as configurações não são populadas (e vice-versa).
- **`CorsConfig` é permissivo**: `allowedOriginPatterns("*")`, `allowedMethods("*")`, `allowedHeaders("*")` — aceito para desenvolvimento, mas deve ser restrito em produção.
- **Sem `IndicadorNaoEncontradoException`**: O módulo `indicador` usa `RuntimeException` genérica, que cai no handler 500.
- **`FileStorageService.salvar()` não valida extensão**: Qualquer tipo de arquivo é aceito.
