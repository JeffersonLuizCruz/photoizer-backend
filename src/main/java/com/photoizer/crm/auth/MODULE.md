# Módulo: Auth

## 1. Responsabilidade
Gerencia autenticação e autorização. Responsável por login (admin e cliente), geração/validação de tokens JWT, refresh token, logout com blocklist, controle de acesso via papéis e cadastro de usuários internos (admin, fotógrafo, editor, agendador).

## 2. Estrutura
```
auth/
├── model/
│   ├── User.java           # Entidade JPA com Lombok (email único, password BCrypt, papel, telefone, ativo, auditInfo)
│   ├── Papel.java          # Enum: ADMIN, FOTOGRAFO, EDITOR, AGENDADOR
│   ├── AuditInfo.java      # @Embeddable: createdAt, updatedAt, createdBy
│   ├── RefreshToken.java   # Entidade JPA: token, userId, expiresAt
│   └── TokenBlocklist.java # Entidade JPA: jti, expiresAt (tokens revogados)
├── repository/
│   ├── UserRepository.java         # JpaRepository + findByEmail, existsByEmail, findByPapel
│   ├── RefreshTokenRepository.java # JpaRepository + findByToken, deleteByUserId
│   └── TokenBlocklistRepository.java # JpaRepository + existsByJti
├── service/
│   ├── AuthService.java         # Login: busca user, valida BCrypt + ativo, gera JWT + refresh token
│   ├── UserService.java         # CRUD de usuários, delegado pelo FotografoService
│   └── RefreshTokenService.java # Refresh token, blocklist, revoke
├── api/
│   ├── AuthController.java     # POST /login, POST /refresh, POST /logout
│   ├── UserController.java     # GET /users, GET /users/{id}, POST /users (ADMIN)
│   ├── LoginRequest.java       # Record: email, password
│   ├── LoginResponse.java      # Record: token, refreshToken, nome, email, papel, userId (UUID)
│   ├── CriarUserRequest.java   # Record: email, password, nome, papel, telefone (com @Valid)
│   └── UserResponse.java       # Record: id, email, nome, papel, telefone, ativo + static of(User)
└── config/
    ├── SecurityConfig.java          # SecurityFilterChain: stateless JWT, rotas públicas + authenticated default
    ├── JwtTokenProvider.java        # Geração/validação HMAC-SHA256 (24h access, 7d refresh), claims: sub, email, papel, jti
    └── JwtAuthenticationFilter.java # OncePerRequestFilter: valida token, checa blocklist, rejeita refresh tokens
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
| fotografo | `FotografoService` delega CRUD para `UserService` |

### Eventos
Nenhum. Módulo fundacional.

## 4. Fluxos Principais

### Fluxo 1: Login Admin
1. `POST /api/v1/auth/login` (público) → `AuthService.login()`
2. Busca por email → `BadCredentialsException("Email ou senha inválidos")` se não encontrado
3. Valida BCrypt → `BadCredentialsException("Email ou senha inválidos")` se senha inválida
4. Verifica `isAtivo()` → `BadCredentialsException("Email ou senha inválidos")` se inativo
5. Gera JWT via `JwtTokenProvider.generateToken()` + `RefreshToken.create()`
6. Retorna `LoginResponse` com `token`, `refreshToken`, `userId` (UUID)

### Fluxo 2: Refresh Token
1. `POST /api/v1/auth/refresh` (público) → `RefreshTokenService.refreshAccessToken()`
2. Valida refresh token JWT + verifica se está armazenado e não expirado
3. Gera novo access token com mesmo userId/email/papel
4. Retorna `Map<String, String>` com `accessToken`

### Fluxo 3: Logout
1. `POST /api/v1/auth/logout` (autenticado) → `RefreshTokenService`
2. Bloqueia access token na blocklist (`TokenBlocklist` com `jti`)
3. Remove refresh token do banco
4. Retorna `204 No Content`

### Fluxo 4: Autorização de Requisição
1. `JwtAuthenticationFilter.doFilterInternal()`:
   - Extrai `Bearer` token; se ausente/inválido → segue sem auth
   - Rejeita refresh tokens (não autentica)
   - Verifica blocklist → se bloqueado, retorna 401
   - Cria `UsernamePasswordAuthenticationToken` com `ROLE_<papel>` e seta no `SecurityContextHolder`
2. `SecurityConfig.filterChain()` aplica `anyRequest().authenticated()` como default
3. Controle de acesso por role delegado para `@RolesAllowed` nos controllers

### Fluxo 5: CRUD de Usuários
- `GET /api/v1/users` → lista (`UserResponse.of`) — qualquer autenticado
- `GET /api/v1/users/{id}` → `ResponseStatusException(404)` se não encontrado
- `POST /api/v1/users` → cria (ADMIN). Valida email duplicado (`ResponseStatusException(409)`)
- `FotografoService` delega CRUD para `UserService` (métodos `criarFotografo`, `atualizarFotografo`, `toggleStatus`, `remover`)

## 5. Regras Específicas
1. **`User` usa Lombok** (`@Getter/@Setter/@NoArgsConstructor`) + `@Embeddable AuditInfo`. Não estende `BaseEntity`.
2. **`SecurityConfig` simplificado**: apenas rotas públicas (`permitAll`) + `anyRequest().authenticated()`. Autorização por role delegada para `@RolesAllowed` nos controllers.
3. **Refresh token**: armazenado em banco (`RefreshToken`), expira em 7 dias, renovado a cada uso.
4. **Blocklist**: tokens revogados armazenados em `TokenBlocklist` (JPA), verificados a cada request.
5. **`@JsonIgnore` em `getPassword()`** — barreira adicional contra vazamento de hash BCrypt.
6. **Secret JWT**: variável de ambiente `JWT_SECRET` com fallback para dev (`application.properties`).

## 6. Testes
Nenhum teste específico. Apenas `CrmApplicationTests` (smoke).

## 7. Dívidas Técnicas e Melhorias Recomendadas

### 7.1 ~~`SecurityConfig` god config~~ — **RESOLVIDO**
- ~~55 `requestMatchers`~~ → simplificado para `anyRequest().authenticated()` + `@RolesAllowed` nos controllers.

### 7.2 ~~`User` sem Lombok, sem auditoria~~ — **RESOLVIDO**
- Adotado Lombok (`@Getter/@Setter/@NoArgsConstructor`) + `@Embeddable AuditInfo` com `@PrePersist`/`@PreUpdate`.

### 7.3 ~~Tratamento de erros inconsistente~~ — **RESOLVIDO**
- `UserService.buscarPorId` → `ResponseStatusException(404)` em vez de `RuntimeException`
- `UserService.criar` → `ResponseStatusException(409)` para email duplicado
- `AuthService.login` → mensagem uniforme `"Email ou senha inválidos"` (inclusive para inativo)

### 7.4 ~~Modelo de segurança JWT~~ — **RESOLVIDO**
- Refresh token (7 dias) + blocklist para logout + rejeição de refresh tokens no filter.

### 7.5 ~~`LoginResponse.userId` tipado como String~~ — **RESOLVIDO**
- Trocado para `UUID` para consistência com os demais IDs da API.

### 7.6 ~~Entidade `User` expõe o hash de senha~~ — **RESOLVIDO**
- Adicionado `@JsonIgnore` em `getPassword()`. `FotografoController` e `ParceiroController` agora retornam `UserResponse`.

### 7.7 ~~Configuração da secret JWT~~ — **RESOLVIDO**
- Variável de ambiente `JWT_SECRET` com fallback para dev.

### 7.8 DTOs manuais — **P3**
- `UserResponse.of(User)` é uma factory manual — candidata a **MapStruct** quando o mapper central for criado.

### 7.9 ~~`FotografoService` duplica CRUD de User~~ — **RESOLVIDO**
- `FotografoService` agora delega CRUD para `UserService` (métodos `criarFotografo`, `atualizarFotografo`, `toggleStatus`, `remover`).

## 8. Exemplos de arquivos afetados (refatoração Fase 1 + P2)
- `SecurityConfig.java` — simplificado de ~55 para ~15 requestMatchers
- `User.java` — Lombok + AuditInfo (eliminadas ~20 linhas de boilerplate)
- `AuditInfo.java` — novo arquivo @Embeddable
- `RefreshToken.java` — novo arquivo (refresh token storage)
- `TokenBlocklist.java` — novo arquivo (logout blocklist)
- `RefreshTokenRepository.java` — novo repositório
- `TokenBlocklistRepository.java` — novo repositório
- `RefreshTokenService.java` — novo service (refresh + blocklist)
- `JwtTokenProvider.java` — jti claim + refresh token generation
- `JwtAuthenticationFilter.java` — blocklist check + rejeição de refresh tokens
- `AuthController.java` — endpoints /refresh e /logout
- `LoginResponse.java` — adicionado `refreshToken` + `userId` como UUID
- `AuthService.java` — gera refresh token no login
- `UserService.java` — métodos para CRUD de fotógrafos
- `FotografoService.java` — delega CRUD para UserService
- `UserService.java` — `RuntimeException` → `ResponseStatusException(404/409)`
- `FotografoController.java` — retorna `UserResponse` em vez de `User`
- `ParceiroController.java` — retorna `UserResponse` em vez de `User`
- `application.properties` — `JWT_SECRET` via env + `refresh-expiration`
