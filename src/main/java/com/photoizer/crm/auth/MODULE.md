# Módulo: Auth

## 1. Responsabilidade
Gerencia autenticação e autorização. Responsável por login (admin e cliente), geração/validação de tokens JWT, controle de acesso via papéis e cadastro de usuários internos (admin, fotógrafo, editor, agendador).

## 2. Estrutura
```
auth/
├── model/
│   ├── User.java           # Entidade JPA (email único, password BCrypt, papel, telefone, ativo)
│   └── Papel.java          # Enum: ADMIN, FOTOGRAFO, EDITOR, AGENDADOR
├── repository/
│   └── UserRepository.java # JpaRepository + findByEmail, existsByEmail, findByPapel
├── service/
│   ├── AuthService.java    # Login: busca user, valida BCrypt + ativo, gera JWT
│   └── UserService.java    # Lista (todos→UserResponse), busca por ID, cria com senha codificada
├── api/
│   ├── AuthController.java     # POST /api/v1/auth/login (público)
│   ├── UserController.java     # GET /users, GET /users/{id}, POST /users
│   ├── LoginRequest.java       # Record: email, password
│   ├── LoginResponse.java      # Record: token, nome, email, papel, userId
│   ├── CriarUserRequest.java   # Record: email, password, nome, papel, telefone (com @Valid)
│   └── UserResponse.java       # Record: id, email, nome, papel, telefone, ativo + static of(User)
└── config/
    ├── SecurityConfig.java          # SecurityFilterChain: stateless JWT, rotas por papel
    ├── JwtTokenProvider.java        # Geração/validação HMAC-SHA256 (24h), claims: sub, email, papel
    └── JwtAuthenticationFilter.java # OncePerRequestFilter: valida token e monta Authentication pelo claim
```

## 3. Dependências Externas

### Módulos internos
- **shared** → `CorsConfig`, `RateLimitFilter` (usados no `SecurityConfig`)

### Módulos que dependem deste
| Módulo | Uso |
|--------|-----|
| cliente | `ClienteAuthService` usa `JwtTokenProvider` para gerar token de cliente |
| agenda | `Agendamento` tem `@ManyToOne User editor` |
| pacote | `Pacote` tem `@ManyToOne User fotografo/editorResponsavel` |
| edicao/financeiro/foto/contrato/agenda | Referenciam `User` em relacionamentos |

### Eventos
Nenhum. Módulo fundacional.

## 4. Fluxos Principais

### Fluxo 1: Login Admin
1. `POST /api/v1/auth/login` (público) → `AuthService.login()`
2. Busca por email → `BadCredentialsException` se não encontrado/inválido
3. Valida BCrypt e `isAtivo()` → `BadCredentialsException`
4. Gera JWT via `JwtTokenProvider.generateToken(userId, email, papel.name())`
5. Retorna `LoginResponse` (userId como String)

### Fluxo 2: Autorização de Requisição
1. `JwtAuthenticationFilter.doFilterInternal()`:
   - Extrai `Bearer` token; se ausente/inválido → segue sem auth
   - Usa `getUserIdFromToken` + `getPapelFromToken` **apenas das claims** (sem consulta ao banco)
   - Cria `UsernamePasswordAuthenticationToken` com `ROLE_<papel>` e seta no `SecurityContextHolder`
2. `SecurityConfig.filterChain()` aplica `requestMatchers` por papel e termina com `anyRequest().denyAll()`

### Fluxo 3: CRUD de Usuários
- `GET /api/v1/users` → lista (`UserResponse.of`) — qualquer autenticado
- `GET /api/v1/users/{id}` → `RuntimeException` genérica se não encontrado
- `POST /api/v1/users` → cria (ADMIN). Valida email duplicado, codifica BCrypt

## 5. Regras Específicas
1. **`User` é a única entidade que não usa Lombok nem auditoria**: getters/setters manuais, sem `createdAt`/`updatedAt`, sem herança de `BaseEntity`, `@GeneratedValue(GenerationType.UUID)` (`User.java:18`).
2. **`SecurityConfig` centraliza regras de TODOS os módulos**: ~55 `requestMatchers` (`SecurityConfig.java:39-94`) acoplam auth a cada rota dos outros módulos.
3. **Filter não consulta o banco**: monta a autorização 100% das claims. Token continua válido mesmo se o usuário for desativado após o login (a checagem de `ativo` só ocorre no login).
4. **Token de cliente usa papel `"CLIENTE"`** — string que não existe no enum `Papel`; rotas do cliente são protegidas por `permitAll`/`authenticated`, não por `hasRole("CLIENTE")`.

## 6. Testes
Nenhum teste específico. Apenas `CrmApplicationTests` (smoke). Não há teste do `SecurityConfig`/`JwtAuthenticationFilter`/`JwtTokenProvider`.

## 7. Dívidas Técnicas e Melhorias Recomendadas

### 7.1 `SecurityConfig` god config — acoplamento com todos os módulos — **P1**
- **Problema**: ~55 `requestMatchers` enumerando rotas de agenda, edicao, financeiro, ecommerce, etc. (`SecurityConfig.java:39-94`). Qualquer nova rota exige editar este arquivo; qualquer módulo novo exige saber dele.
- **Solução**: `@EnableMethodSecurity` já está ativo (`SecurityConfig.java:20`) mas **nunca é usado** (`@Secured`/`@RolesAllowed`/`@PreAuthorize` ausentes em todo o projeto). Mover a autorização para anotações nos controllers/endpoints de cada módulo (`@RolesAllowed("ADMIN")`, `@PreAuthorize("hasAnyRole(...)")`), mantendo no `SecurityConfig` apenas o fluxo público/`authenticated` default.

### 7.2 `User` sem Lombok, sem auditoria, sem consistência — **P1**
- `User.java` possui ~60 linhas de boilerplate manual (getters/setters) (`User.java:49-62`), sem campos de auditoria (`createdAt`/`updatedAt`), divergente das demais entidades.
- **Solução**: adotar Lombok (`@Getter/@Setter/@NoArgsConstructor/@Builder`) + compor `AuditInfo` (pós-remoção da herança `BaseEntity`, ver `shared/MODULE.md §7.1`) para rastreabilidade.

### 7.3 Tratamento de erros inconsistente — **P1**
- `UserService.buscarPorId` lança `RuntimeException` genérica (`UserService.java:34`) → cai no handler 500 em vez de 404.
- `AuthService.login` lança `BadCredentialsException` com mensagem de erro de autenticação. A mensagem `"Usuário inativo"` é distinta da de senha errada — permite **enumeração de usuários** por diferença de resposta (`AuthService.java:34-36`).
- **Solução** (integra com `shared/MODULE.md §7.3 — hierarquia central): criar `NotFoundException` em `shared`, usar no `UserService`; unificar a mensagem de login em uma única `BadCredentialsException("Email ou senha inválidos")` (mesmo para inativo).

### 7.4 Modelo de segurança JWT — **P2**
- `JwtTokenProvider.generateToken` (`:28-38`) inclui `email` e `papel` como claims sem expiração curta para senha; token de 24h sem refresh token, logout ou revogação (`auth/MODULE.md` §5 do original não cobre; no código não há endpoint de logout).
- **Solução**: implementar refresh token (rota `/auth/refresh`) e logout com blocklist/`jti`; checar `ativo` a cada requisição autenticada (ou aceitar trade-off documentado) quando a desativação imediata for requisito.

### 7.5 `LoginResponse.userId` tipado como String — **P3**
- `LoginResponse.userId` é `String` (`LoginResponse.java:10`) embora `User.id` seja `UUID`. Trocar para `UUID` para consistência com os demais IDs da API.

### 7.6 Entidade `User` expõe o hash de senha — **P3**
- `getPassword()` público (`User.java:53`) permite que qualquer componente com a entidade acesse o hash BCrypt. Garantir que o hash nunca saia em respostas e considerar `@JsonIgnore` como barreira adicional de defesa.

### 7.7 Configuração da secret JWT — **P2**
- `Keys.hmacShaKeyFor` exige segredo ≥ 32 bytes e lança erro de startup se for curto. A secret em `application.properties` deve vir de variável de ambiente/secret manager — nunca versionada.

### 7.8 DTOs manuais — **P3**
- `UserResponse.of(User)` (`UserResponse.java:15-17`) é uma factory manual — candidata a **MapStruct** quando o mapper central for criado (ver plano geral).

## 8. Exemplos de arquivos afetados
- `SecurityConfig.java:39-94` — `requestMatchers` a migrar para anotações; `UserService.java:34` — `RuntimeException` → `NotFoundException`; `AuthService.java:30-36` — mensagens de login a unificar; `User.java:49-62` — boilerplate a trocar por Lombok; `LoginResponse.java:10` — tipo do `userId`.