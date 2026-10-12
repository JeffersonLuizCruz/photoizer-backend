# 📖 AGENTS.md - Contexto do Projeto para IA

## 1. Visão Geral e Stack Tecnológica
- **Objetivo do Projeto:** API REST do CRM Photoizer para estúdio de fotografia. Gerencia clientes, agendamentos de ensaios, pacotes, edição de fotos, financeiro, e-commerce (galeria pública + venda de fotos extras), comissões de indicação, notificações e geração de documentos.
- **Linguagens e Frameworks:** Java 25, Spring Boot 4.1.0, Spring Modulith 2.1.0, Spring Data JPA, Spring Security, Spring MVC.
- **Banco de Dados:** PostgreSQL em **todos** os ambientes (inclusive `dev`), schema versionado por **Flyway** (`src/main/resources/db/migration`, `V1__baseline.sql`), `ddl-auto=validate`. Dev local usa o Postgres do `docker-compose.dev.yml` (defaults em `application.properties`). Os **testes** usam H2 em memória (`src/test/resources/application.properties`).
- **Bibliotecas Críticas:**
  - **jjwt 0.12.6** — Geração e validação de tokens JWT (HMAC-SHA256).
  - **Lombok** — `@Getter/@Setter/@SuperBuilder/@NoArgsConstructor` em entidades.
  - **SpringDoc OpenAPI 3.1.1** — Documentação Swagger UI. **A linha v3 é obrigatória para Spring Boot 4** (o v2.x atende só Boot 3.x). Não rebaixar para 2.8.x.
  - **Thumbnailator 0.4.20** — Geração de thumbnails e watermarks.
  - **Logstash + Brave Tracing + Micrometer** — Logging estruturado e tracing distribuído.
  - **AOP (AspectJ)** — LoggingAspect para controllers, services e repositories.
- **Estilo e Formatação:**
  - Build: Maven do sistema (`mvn`) — **não existe `./mvnw`** (apenas `mvnw.cmd`).
  - DTOs são Java Records.
  - Valores monetários sempre `BigDecimal(10,2)`, IDs UUID.
  - Validação: `jakarta.validation` (`@NotBlank`, `@Positive`, `@Email`, etc).

## 2. Estrutura de Diretórios
- `src/main/java/com/photoizer/crm/` — pacote raiz.
  - `CrmApplication.java` — `@SpringBootApplication @Modulith @EnableScheduling`.
  - `shared/` — Infraestrutura compartilhada:
    - `model/BaseEntity` — `@MappedSuperclass` com `id` (UUID), `createdAt`, `updatedAt`, `createdBy`.
    - `config/` — CorsConfig, DataSeeder, LoggingConfig, OpenApiConfig.
    - `config/openapi/` — `OpenApiTags` (catálogo de tags), `GlobalOpenApiCustomizer`
      (segurança + respostas de erro padrão) e `OpenApiTagsCustomizer` (ordem/tags).
    - `exception/` — `ErrorResponse` (record), `GlobalExceptionHandler` (`@RestControllerAdvice`).
    - `logging/` — `LoggingAspect`, `SensitiveDataMask` (mascara CPF, telefone, email em logs).
    - `storage/` — `FileStorageService` interface + `LocalFileStorageService` (salva em `uploads/`).
    - `api/PageResponse` — record genérico para respostas paginadas.
  - `{modulo}/` — 16 módulos de negócio (ver seção 3).
- `src/main/resources/` — `application.properties` + profiles (`dev`, `homolog`, `prod`) + `db/migration/` (Flyway).
- `src/test/java/` — testes de segurança (autorização por controller, CSRF, cookies) e sanitização.
- `uploads/` — diretório de upload de arquivos.
- `data/` — banco H2 em arquivo (`crmdb.mv.db`).

## 3. Módulos de Negócio (Spring Modulith)

Cada módulo segue a estrutura: `model/`, `repository/`, `service/`, `api/`, DTOs inline.

| Módulo | Entidade(s) Principal(is) | API Base |
|--------|--------------------------|----------|
| **auth** | `User` (UUID, email único, password BCrypt, papel enum: ADMIN/FOTOGRAFO/EDITOR/AGENDADOR) | `/api/v1/auth` |
| **cliente** | `Cliente` (UUID, telefone único, cpf único, origem enum: INDICACAO/ANUNCIO/OUTROS, senhaHash) | `/api/v1/clientes` |
| **agenda** | `Agendamento` (@ManyToOne Cliente/Pacote/User, status enum 10 estados, tokenGaleria UUID), `Tarefa` (tipo enum: EDITAR_FOTOS/ENVIAR_PARA_SELECAO/ENTREGA_FINAL) | `/api/v1/agenda` |
| **pacote** | `Pacote` (@ManyToOne fotografo/editorResponsavel, valorBase/precoFotoExtra BigDecimal) | `/api/v1/pacotes` |
| **ecommerce** | `Pedido`, `CompraExtra` (status enum: AGUARDANDO_COMPROVANTE/AGUARDANDO_CONFIRMACAO/PAGA/CANCELADA), `ItemCarrinho`, `Favorito`, `Cupom`, `Sessao`, `Avaliacao` | `/api/v1/ecommerce` |
| **foto** | `FotoEnsaio` (status enum: INEDITA/PUBLICADA/AGUARDANDO_*/PAGA, tags, destaque, compraExtraId) | `/api/v1/fotos` |
| **edicao** | `Edicao` (status: AGUARDANDO_RAW/RAW_ENVIADOS/EM_EDICAO/EDICAO_CONCLUIDA), `FotoEdicao` (status: RAW/EM_EDICAO/EDITADO) | `/api/v1/edicao` |
| **financeiro** | `Pagamento`, `FotoExtra`, `VideoExtra` (@ManyToOne Agendamento) | `/api/v1/financeiro` |
| **comissao** | `Indicacao` (percentual, valorComissao, status String: PENDENTE/PAGA/CANCELADA) | `/api/v1/comissoes` |
| **indicador** | `Indicador` (nome, telefone) | `/api/v1/indicadores` |
| **config** | `Configuracao` (chave-valor, chave = @Id String) | `/api/v1/config` |
| **dashboard** | (consultas agregadas, sem entidade) | `/api/v1/dashboard` |
| **despesa** | `Despesa` (descricao, valor, categoria, data) | `/api/v1/despesas` |
| **notificacao** | `Notificacao` (userId, titulo, mensagem, lida, link) | `/api/v1/notificacoes` |
| **documento** | (geração de PDF/contrato, sem entidade) | `/api/v1/documentos` |

## 4. Padrões e Convenções de Código (CRUCIAL)
- **Arquitetura:** Spring Modulith. Módulos se comunicam via **Application Events** (`ApplicationEvent` + `@EventListener`), nunca via importação direta de service de outro módulo. Eventos: `AgendamentoCriadoEvent`, `AgendamentoConfirmadoEvent`, `AgendamentoRealizadoEvent`, `AgendamentoCanceladoEvent`, `PagamentoFinalRegistradoEvent`, `EdicaoConcluidaEvent`, `FotosPublicadasEvent`, `RawEnviadosEvent`, `CompraExtraCriadaEvent`, `CompraExtraConfirmadaEvent`.
- **Controllers:** `@RestController @RequestMapping("/api/v1/{recurso}")`, `@Tag` + `@Operation` para Swagger. Injeção via construtor (sem `@Autowired` em campo). Retornam DTOs.
- **Services:** `@Service`, injeção via construtor. Lógica de negócio com `@Transactional` onde necessário.
- **Repositories:** `@Repository` extends `JpaRepository` ou `JpaSpecificationExecutor`.
- **Entidades:**
  - Maioria estende `BaseEntity` (`@MappedSuperclass` com Lombok `@SuperBuilder`). Exceções: `User`, `Notificacao`, `Configuracao` (não estendem BaseEntity — `User` nem usa Lombok).
  - Relacionamentos: `@ManyToOne(fetch = LAZY)` com `@JoinColumn`.
  - Enums armazenados como String (`@Enumerated(STRING)`).
- **DTOs:** Java Records com `static of(Entity)` factory method. `Request` com `@Valid`, `Response` para output.
- **Paginação:** Spring Data `Page` + `PageResponse<T>` (data, total, page, perPage, totalPages).
- **Validação:** `jakarta.validation` nos requests. Erros capturados pelo `GlobalExceptionHandler`.
- **Exceções de domínio:** Classes específicas por módulo (`ClienteNaoEncontradoException`, `ConflitoDeAgendaException`). `GlobalExceptionHandler` mapeia para `ErrorResponse` com HTTP status adequado.
- **Segurança:**
  - Stateless JWT (HMAC-SHA256, 24h). `JwtAuthenticationFilter` (OncePerRequestFilter) + `JwtTokenProvider`.
  - Rotas públicas: `/api/v1/auth/login`, `/api/v1/auth/cliente/*`, `/api/v1/ecommerce/galeria/**`, `/api/v1/ecommerce/fotos/**`, H2-console, Swagger, `/actuator/health`.
  - Papéis: ADMIN, FOTOGRAFO, EDITOR, AGENDADOR (configurados via requestMatchers).
  - Senhas: BCryptPasswordEncoder.
- **Upload de Arquivos:**
  - Máx: 50MB/arquivo, 200MB/request.
  - Salvos via `LocalFileStorageService` sob `app.storage.upload-dir` (env `UPLOAD_DIR`); o caminho gravado no banco é **relativo** e resolvido por `StoragePathResolver`.
  - Servidos via `FileServeHelper` (valida path traversal).
  - Imagens passam por watermark + thumbnail via Thumbnailator.
- **Logging:** AOP intercepta controllers (método + duração), services (args mascarados + resultado), repositories (trace). Dados sensíveis mascarados via `SensitiveDataMask`.
- **DataSeeder:** `CommandLineRunner` que popula 5 usuários e 6 configurações se vazio.
- **Nomenclatura:**
  - Classes: PascalCase (`AgendamentoService`).
  - Pacotes: minúsculo singular (`cliente`, `ecommerce`).
  - Records DTO: `{Entidade}Request`, `{Entidade}Response`.
  - Enums: PascalCase (`StatusAgendamento`, `Papel`).

## 5. Fluxos Principais e Pontos de Entrada
- **Ponto de Entrada:** `CrmApplication.java` (Spring Boot + Modulith + Scheduling).
- **Autenticação Admin:**
  - `POST /api/v1/auth/login` → verifica BCrypt → gera JWT (claims: `sub`=userId, `email`, `papel`).
  - Emite cookies `HttpOnly` (`photoizer_access`/`photoizer_refresh`) + `XSRF-TOKEN`; mutações exigem
    header `X-XSRF-TOKEN` (double-submit). `Authorization: Bearer` continua aceito (compatibilidade).
  - O refresh token é persistido **apenas como hash SHA-256** e rotaciona a cada `/auth/refresh`
    (reuse detection revoga a família). Ver `RefreshTokenService`.
  - `GET /api/v1/auth/me` devolve os dados do usuário autenticado (fonte de verdade para o frontend).
  - `JwtAuthenticationFilter` valida cookie ou header em toda requisição.
  - **`JWT_SECRET` é obrigatório fora de dev** (`application.properties` usa `${JWT_SECRET}` sem default).
    O perfil `dev` define um segredo descartável em `application-dev.properties`; prod/homolog exigem
    a variável de ambiente (o boot falha sem ela).
  - Admin de bootstrap: `AuthUserSeeder` só roda em `dev`; fora dele, o `AdminBootstrapSeeder` cria o
    primeiro ADMIN a partir de `ADMIN_EMAIL`/`ADMIN_PASSWORD` (falha o boot se ausentes e `users` vazio).
- **Autenticação Cliente:**
  - `POST /api/v1/auth/cliente/registro` e `/auth/cliente/login` → `ClienteAuthService`.
- **Fluxo de Agendamento:**
  1. `POST /api/v1/agenda` → `AgendamentoCriadoEvent` → cria comissão (se indicação) + gera contrato.
  2. Confirmação → `AgendamentoConfirmadoEvent` → agenda lembretes.
  3. Realização → `AgendamentoRealizadoEvent` → `FinanceiroEventListener`.
  4. Pagamento final → `PagamentoFinalRegistradoEvent` → cria edição + marca comissão como paga.
- **Fluxo de Edição:**
  1. Upload RAW → `POST /api/v1/edicao/{id}/upload-raw`.
  2. Upload editado → `POST /api/v1/edicao/{id}/upload-editado`.
  3. Revisão → `POST /api/v1/edicao/{id}/revisar`.
  4. Publicação → `FotosPublicadasEvent`.
- **Fluxo de E-commerce (Galeria do Cliente):**
  1. Cliente acessa `/g/{token}` (tokenUUID 15 dias).
  2. Seleciona fotos, adiciona ao carrinho.
  3. Checkout → gera `CompraExtra`.
  4. Upload comprovante → confirmação admin → fotos liberadas.
- **Swagger:** `/swagger-ui.html` (UI e `/v3/api-docs` livres em dev, staff autenticado em homolog, negados em prod).
  Padrão completo em `docs/API_DOCUMENTATION_STANDARD.md`. Spec versionada (`docs/api/openapi.json`) gerada por
  `mvn -Dtest=OpenApiSpecExporter test`; validada no build por `OpenApiContractTest` e pela checagem
  de spec desatualizada no CI.

## 6. Comandos Úteis
- `docker compose -f docker-compose.dev.yml up -d` — Sobe o PostgreSQL local (porta 5432).
- `mvn spring-boot:run` — Sobe servidor (porta 8080) no perfil `dev`, usando o Postgres local.
- `mvn clean package` — Build + testes.
- `mvn test` — Executa testes (H2 em memória; não exige Postgres).
- `mvn -Dtest=SchemaBaselineGenerator test` — Gera o DDL Postgres em `target/schema-postgres.sql` (ferramenta de baseline; não roda no build padrão).
- `JWT_SECRET=<segredo> SPRING_PROFILES_ACTIVE=prod SPRING_DATASOURCE_URL=<jdbc> ... mvn spring-boot:run` — Sobe em prod.
- `java -jar target/crm-0.0.1-SNAPSHOT.jar` — Executa o JAR.
