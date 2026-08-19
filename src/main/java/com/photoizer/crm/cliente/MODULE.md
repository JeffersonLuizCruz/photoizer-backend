# Módulo: Cliente

## 1. Responsabilidade
Gerencia o cadastro de clientes e a autenticação de clientes no e-commerce. Clientes podem ser criados manualmente (admin) ou automaticamente durante o fluxo de agendamento (via `ClienteService.criar()` chamado pelo módulo `agenda`).

## 2. Estrutura
```
cliente/
├── model/
│   ├── Cliente.java        # Entidade JPA (extends BaseEntity): nome, telefone, email, cpf, origem, senhaHash, dataCadastro, preferencias
│   └── OrigemCliente.java  # Enum: INDICACAO, ANUNCIO, OUTROS
├── repository/
│   └── ClienteRepository.java # JpaRepository + busca por telefone, cpf, email, nome (contendo), paginado, countByDataCadastro
├── service/
│   ├── ClienteService.java     # CRUD + busca textual (telefone exato → contém → nome)
│   └── ClienteAuthService.java # Registro/login/atualizarPerfil do cliente + geração de JWT
├── api/
│   ├── ClienteController.java      # CRUD + GET /{id}/agendamentos
│   ├── ClienteAuthController.java  # POST /registro, /login, GET/PUT /perfil, GET /agendamentos
│   ├── ClienteRegistroRequest.java # Record: nome, email, telefone, senha, preferencias
│   ├── ClienteLoginRequest.java    # Record: email, senha
│   ├── ClienteAuthResponse.java    # Record: token, id, nome, email, telefone
│   ├── AtualizarPerfilRequest.java # Record: nome, telefone, email, cpf, cidade, estado
│   └── AgendamentoClienteResponse.java # Record + static of(Agendamento, ...)
└── exception/
    └── ClienteNaoEncontradoException.java # RuntimeException com UUID
```

## 3. Dependências Externas

### Módulos internos importados — **[VIOLAÇÕES Modulith]**
- **agenda** → `AgendamentoService` + `AgendamentoResponse` injetados em `ClienteController.java:3-4,34` e `ClienteAuthController.java:3,30`.
- **auth** → `JwtTokenProvider` usado em `ClienteAuthService.java:3,29` (para gerar token de cliente).
- **shared** → `BaseEntity`, `PageResponse`.

### Módulos que dependem deste
| Módulo | Uso |
|--------|-----|
| agenda | `AgendamentoService` resolve/cria `Cliente` no fluxo de agendamento |
| contrato | Gera contrato com dados do cliente |
| financeiro | Relatórios referenciam cliente |

### Eventos
Nenhum. Não publica nem consome eventos.

## 4. Fluxos Principais

### Fluxo 1: CRUD de Clientes (Admin)
- `POST /api/v1/clientes` → recebe a **entidade JPA** `Cliente` como body (`ClienteController.java:43`).
- `GET /api/v1/clientes` → paginado com busca (`PageResponse<Cliente>`), retorna **entidade**.
- `PUT /api/v1/clientes/{id}` → merge manual campo a campo (`ClienteService.atualizar`).
- `DELETE /api/v1/clientes/{id}` → `ClienteNaoEncontradoException` se ausente.
- `GET /api/v1/clientes/{id}/agendamentos` → delega ao módulo `agenda`.

### Fluxo 2: Autenticação Cliente (E-commerce)
- `POST /api/v1/auth/cliente/registro` (público): valida email/telefone únicos; cria `Cliente` com `senhaHash` BCrypt, `origem=OUTROS`, `dataCadastro=now`; gera JWT com papel **"CLIENTE"** (string).
- `POST /api/v1/auth/cliente/login` (público): busca por email (case-insensitive), valida BCrypt, gera token.
- `GET/PUT /api/v1/auth/cliente/perfil`: resolve `userId` do token (`@AuthenticationPrincipal String userId`) e opera no cliente. Retorna a entidade `Cliente` inteira.

### Fluxo 3: Criação de Cliente via Agendamento
- `AgendamentoService.criarAgendamento()`: `clienteId` → busca existente; senão telefone; senão cria novo `Cliente` **sem** `senhaHash`/`dataCadastro`.

## 5. Regras Específicas
1. **API expõe a entidade `Cliente` como body de entrada e saída**, inclusive o campo `senhaHash` (BCrypt) em todas as respostas (`ClienteController.java:43,50,64,70`; `ClienteAuthController.java:53,59`). **[CRÍTICO — ver 7.3]**
2. **`dataCadastro` inconsistente**: só é preenchido em `ClienteAuthService.registrar()` (`:52`). Clientes criados via admin/agenda ficam com `dataCadastro = null`.
3. **Telefone único** (`Cliente.java:41`) com restrição duplicada (coluna `unique = true` + `@UniqueConstraint` da tabela, `:25`).
4. **`JoinColumn` de identificação frágil**: `telefone` usado como chave de busca em 3 níveis no módulo agenda.
5. **Rotas `/perfil` e `/agendamentos` de cliente**: não há `requestMatchers` explícito para `/api/v1/auth/cliente/perfil|agendamentos` no `SecurityConfig` — caem em `anyRequest().denyAll()`. **[VERIFICAR]**

## 6. Testes
Nenhum teste específico.

## 7. Dívidas Técnicas e Melhorias Recomendadas

### 7.1 Contrato da API acoplado à entidade JPA — **[CRÍTICO] P1**
- O controller serializa `Cliente` (entidade) como request/response (`ClienteController.java:43,50,64,70`). Qualquer mudança na entidade altera o contrato da API; campos internos (id, timestamps, `senhaHash`) vazam para o cliente.
- **Solução (Clean Architecture)**: criar `ClienteRequest` (validação) e `ClienteResponse` (sem dados sensíveis) e mapear com **MapStruct**. Services passam a receber/retornar DTOs (ou um `ClienteCommand`), nunca a entidade como input (o que também elimina `ClienteService.atualizar(UUID, Cliente)`).

### 7.2 Vazamento do hash de senha — **[CRÍTICO] P1**
- `GET /perfil`, `PUT /perfil` e todos os endpoints de `ClienteController` retornam a entidade `Cliente`, que contém `senhaHash` (`Cliente.java:70-72`). O hash BCrypt fica visível na API.
- **Solução**: nunca serializar a entidade; usar `ClienteResponse` sem `senhaHash` (ver 7.1); para escrita usar request DTO.

### 7.3 `dataCadastro` inconsistente — **P2**
- Só o fluxo de registro preenche `dataCadastro`. **Solução**: extrair `dataCadastro = now` para um listener do ciclo de vida (`@PrePersist` em entidade ou auditoria automática) ou defini-lo em `ClienteService.criar`.

### 7.4 Busca fragmentada e N queries — **P2**
- `buscarPorSearch` (`ClienteService.java:35-48`) roda até 3 queries e faz `distinct()` em memória. `listarPaginado` duplica a lógica de busca com implementação diferente (`:77-82`).
- **Solução**: consolidar em `JpaSpecificationExecutor`/Specification única (pesquisa por nome OU telefone com paginação), eliminando métodos duplicados.

### 7.5 Violações Modulith — **P1**
- `cliente/api` → `agenda/service` (`ClienteController.java:34`, `ClienteAuthController.java:30`); `cliente/service` → `auth/config` (`ClienteAuthService.java:29`).
- **Solução**: agendamentos do cliente devem ser entregues por um endpoint no módulo `agenda` (ou evento `ClienteConsultadoEvent`); token de cliente deve passar por `auth` via um service público (ex.: `TokenService`), não por acesso direto ao `JwtTokenProvider`.

### 7.6 Exposição da entidade → polimorfismo/DTO — **P3**
- Manter `AgendamentoClienteResponse` com `static of()` é candidato a **MapStruct** quando o mapper central for criado (mundo `cliente` + `agenda`).

### 7.7 `ClienteAuthService` retorna entidade — **P2**
- `atualizarPerfil` e endpoints `/perfil` devolvem `Cliente` (`ClienteAuthService.java:77`). Mesmo tratamento do 7.1/7.2.

### 7.8 Fortuna de getters/setters expostos — **P2**
- `@Setter` global (`Cliente.java:29`) permite sobrescrever `senhaHash`, `dataCadastro` e `id` de fora do domínio. **Solução**: `@Setter(AccessLevel.PRIVATE)` + métodos de domínio (ex.: `definirSenha`, `atualizarDados`).

## 8. Exemplos de arquivos afetados
- `ClienteController.java:43,50,64,70` — entidade no contrato da API + vazamento de `senhaHash`; `ClienteAuthController.java:53,59` — idem; `ClienteService.java:35-48,77-82` — buscas duplicadas; `Cliente.java:24-27,70-72` — unique duplicado e `senhaHash` serializável; `ClienteAuthService.java:58` — token com papel "CLIENTE" fora do enum.