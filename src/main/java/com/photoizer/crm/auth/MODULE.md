# Módulo: Auth

## 1. Responsabilidade
Gerencia autenticação e autorização do sistema. Responsável por login (admin e cliente), geração/validação de tokens JWT, controle de acesso via papéis, e cadastro de usuários internos (admin, fotógrafo, editor, agendador).

## 2. Estrutura
```
auth/
├── model/
│   ├── User.java           # Entidade JPA (email único, password BCrypt, papel)
│   └── Papel.java          # Enum: ADMIN, FOTOGRAFO, EDITOR, AGENDADOR
├── repository/
│   └── UserRepository.java # JpaRepository + findByEmail, existsByEmail
├── service/
│   ├── AuthService.java    # Login: busca user, valida BCrypt, gera JWT
│   └── UserService.java    # CRUD de usuários (lista, busca, cria com senha codificada)
├── api/
│   ├── AuthController.java     # POST /api/v1/auth/login (público)
│   ├── UserController.java     # GET /api/v1/users, GET/{id}, POST (admin-only)
│   ├── LoginRequest.java       # Record: email, password
│   ├── LoginResponse.java      # Record: token, nome, email, papel, id
│   ├── CriarUserRequest.java   # Record: email, password, nome, papel, telefone
│   └── UserResponse.java       # Record: id, email, nome, papel, ativo, telefone
└── config/
    ├── SecurityConfig.java         # SecurityFilterChain: stateless JWT, role-based matchers
    ├── JwtTokenProvider.java       # Geração e validação HMAC-SHA256 (24h), claims: sub, email, papel
    └── JwtAuthenticationFilter.java # OncePerRequestFilter: extrai token, busca user, seta SecurityContext
```

## 3. Dependências Externas

### Módulos internos
- **shared** → `CorsConfig` (usado no SecurityConfig)

### Módulos que dependem deste
| Módulo | Uso |
|--------|-----|
| cliente | `ClienteAuthService` usa `JwtTokenProvider` para gerar token de cliente |
| agenda | Entidade `Agendamento` tem `@ManyToOne User editor` |
| edicao/foto/financeiro/etc | Diversas entidades referenciam `User` |

### Eventos
Nenhum. Módulo fundacional — não publica nem consome eventos.

## 4. Fluxos Principais

### Fluxo 1: Login Admin
1. `POST /api/v1/auth/login` (público) → `AuthController.login(LoginRequest)`
2. `AuthService.login()`:
   - Busca `User` por email → `BadCredentialsException` se não encontrado
   - Verifica BCrypt `passwordEncoder.matches()` → `BadCredentialsException` se inválido
   - Verifica `user.isAtivo()` → `BadCredentialsException` se inativo
   - Gera token JWT via `JwtTokenProvider.generateToken(userId, email, papel.name())`
   - Retorna `LoginResponse(token, nome, email, papel, id)`

### Fluxo 2: Autorização de Requisição
1. `JwtAuthenticationFilter.doFilterInternal()`:
   - Extrai token do header `Authorization: Bearer <token>`
   - Valida com `jwtTokenProvider.validateToken()`
   - Extrai `userId` do token, busca `User` no banco
   - Cria `UsernamePasswordAuthenticationToken` com `userId` como principal + `papel.name()` como authority ROLE_*
   - Seta no `SecurityContextHolder`
2. `SecurityConfig.filterChain()` aplica `requestMatchers` por papel

### Fluxo 3: CRUD de Usuários
- `GET /api/v1/users` → lista todos (qualquer autenticado)
- `GET /api/v1/users/{id}` → busca por ID
- `POST /api/v1/users` → cria (apenas ADMIN). Valida email duplicado. Codifica senha com BCrypt. Aceita `CriarUserRequest` (inclui `UUID papel` que é convertido via `Papel.valueOf`).

## 5. Regras Específicas
1. **Único módulo que não usa BaseEntity/Lombok**: `User` não estende `BaseEntity`, não tem `@SuperBuilder`, usa construtor próprio com `id` manual, getters/setters escritos à mão.
2. **SecurityConfig centraliza regras de todos os módulos**: As autorizações de `edicao`, `agenda`, `ecommerce`, etc. estão todas aqui. Qualquer mudança de permissão exige alterar este arquivo.
3. **Token para cliente**: `ClienteAuthService` (módulo `cliente`) gera token com papel `"CLIENTE"` — string que **não** existe em `Papel` enum. Rotas clientes são protegidas por `requestMatchers` públicos, não por role.
4. **JwtAuthenticationFilter faz consulta ao banco por requisição**: `userRepository.findById(userId)` é chamado em cada request autenticado. Para alta frequência, seria mais eficiente extrair dados das claims.
5. **Sem refresh token, logout ou forgot password**: O módulo não implementa renovação de token, invalidação ou recuperação de senha.

## 6. Testes
Nenhum teste específico para este módulo. Apenas `CrmApplicationTests`.

## 7. Pontos de Atenção
- **`CriarUserRequest.papel` é UUID (não enum)**: O controller recebe um UUID e faz `Papel.valueOf()` — se o valor não corresponder exatamente ao nome do enum, lança `IllegalArgumentException`.
- **`User.id` não é gerado automaticamente**: A entidade usa `@GeneratedValue` strategy padrão (`AUTO`). Para consistência com `BaseEntity`, seria ideal UUID.
- **`User.ativo` não tem setter público**: O campo `ativo` é `boolean` com default `true`, mas não há endpoint para ativar/desativar usuário.
- **`JwtTokenProvider.secretKey` é derivada da string config**: Se a secret for alterada, todos os tokens existentes são invalidados.
- **`SecurityConfig` usa `@EnableMethodSecurity`**: Mas o projeto não usa `@Secured` ou `@PreAuthorize` em lugar nenhum — toda autorização é via `requestMatchers`.
