# Módulo: Cliente

## 1. Responsabilidade
Gerencia o cadastro de clientes do estúdio e a autenticação de clientes no e-commerce. Clientes podem ser criados manualmente (pelo admin) ou automaticamente durante o fluxo de agendamento (via `ClienteService.criar()` chamado pelo módulo `agenda`).

## 2. Estrutura
```
cliente/
├── model/
│   ├── Cliente.java        # Entidade JPA (extends BaseEntity, @SuperBuilder)
│   └── OrigemCliente.java  # Enum: INDICACAO, ANUNCIO, OUTROS
├── repository/
│   └── ClienteRepository.java # JpaRepository + busca por telefone, nome, email
├── service/
│   ├── ClienteService.java     # CRUD completo + busca textual (telefone exato, contém, nome)
│   └── ClienteAuthService.java # Registro/login de cliente e-commerce + atualizar perfil
├── api/
│   ├── ClienteController.java      # CRUD + GET /{id}/agendamentos
│   ├── ClienteAuthController.java  # POST /registro, POST /login, PUT /perfil
│   ├── ClienteRegistroRequest.java # Record: nome, email, telefone, senha, preferencias
│   ├── ClienteLoginRequest.java    # Record: email, senha
│   ├── ClienteAuthResponse.java    # Record: token, id, nome, email, telefone
│   ├── AtualizarPerfilRequest.java # Record: nome, telefone, email, cpf, cidade, estado
│   └── AgendamentoClienteResponse.java # Record para listagem de agendamentos do cliente
└── exception/
    └── ClienteNaoEncontradoException.java # RuntimeException com UUID
```

## 3. Dependências Externas

### Módulos internos (importados diretamente — violam Modulith)
- **agenda** → `AgendamentoService`, `AgendamentoResponse` (usado em `ClienteController.listarAgendamentos()`) — **[VIOLAÇÃO]**
- **auth** → `JwtTokenProvider` (usado em `ClienteAuthService` para gerar token de cliente)
- **shared** → `BaseEntity`

### Módulos que dependem deste
| Módulo | Uso |
|--------|-----|
| agenda | `AgendamentoService` importa `Cliente`, `ClienteRepository`, `OrigemCliente`, `ClienteNaoEncontradoException` |
| comissao | `IndicacaoResponse` referencia `Cliente` via `Agendamento.getCliente()` |
| documento | Gera contrato com dados do cliente |

### Eventos
Nenhum. Este módulo não publica nem consome eventos.

## 4. Fluxos Principais

### Fluxo 1: CRUD de Clientes (Admin)
- `POST /api/v1/clientes` → cria cliente (recebe entidade JPA diretamente como `@RequestBody`)
- `GET /api/v1/clientes` → lista paginado com busca por nome ou telefone (`search` param)
- `GET /api/v1/clientes/{id}` → busca por ID
- `PUT /api/v1/clientes/{id}` → atualiza (merge manual campo a campo no service)
- `DELETE /api/v1/clientes/{id}` → exclui (lança `ClienteNaoEncontradoException` se não existir)
- `GET /api/v1/clientes/{id}/agendamentos` → lista agendamentos do cliente (chama `AgendamentoService.listarPorClienteId()`)

### Fluxo 2: Autenticação Cliente (E-commerce)
- `POST /api/v1/auth/cliente/registro` (público) → `ClienteAuthService.registrar()`:
  - Valida email único e telefone único
  - Cria `Cliente` com `senhaHash = BCrypt(password)`, `origem = OUTROS`, `dataCadastro = now`
  - Gera JWT com papel `"CLIENTE"` (string, não enum)
  - Retorna `ClienteAuthResponse` com token
- `POST /api/v1/auth/cliente/login` (público) → `ClienteAuthService.login()`:
  - Busca por email, valida BCrypt, gera JWT
  - Retorna `ClienteAuthResponse`
- `PUT /api/v1/auth/cliente/perfil` → `ClienteAuthService.atualizarPerfil()`:
  - Atualiza nome, telefone, email, cpf, cidade, estado

### Fluxo 3: Criação de Cliente via Agendamento
1. `AgendamentoService.criarAgendamento()` no módulo `agenda`:
   - Se `clienteId` informado → busca existente via `ClienteService.buscarPorId()`
   - Se não → busca por telefone via `ClienteRepository.findByTelefone()`
   - Se não encontrado → cria novo `Cliente` com `origem` do request
2. O `Cliente` recém-criado não tem `senhaHash` — só poderá acessar o e-commerce após registro.

## 5. Regras Específicas
1. **Controller expõe entidade JPA**: `ClienteController.criar()` e `atualizar()` recebem `@RequestBody Cliente` diretamente (não um DTO). Isso expõe campos internos e acopla a API ao modelo.
2. **`dataCadastro` inconsistente**: Só é preenchido em `ClienteAuthService.registrar()`. Clientes criados via `ClienteService.criar()` (pelo admin ou pelo módulo agenda) ficam com `dataCadastro = null`.
3. **Telefone como identificador único**: `@Column(unique = true)` e também usado como chave de busca em 3 níveis no módulo agenda.
4. **Senha opcional**: Clientes vindos de agendamento não têm senha. Só clientes registrados via e-commerce têm `senhaHash`.
5. **`ClienteController` viola Modulith**: Injeta `AgendamentoService` diretamente para o endpoint `/{id}/agendamentos`. O correto seria usar evento ou consulta agregada no módulo `agenda`.

## 6. Testes
Nenhum teste específico para este módulo.

## 7. Pontos de Atenção
- **DTO vs Entidade**: O controller usa `Cliente` como request/response body. Não há separação entre camada de API e modelo JPA. Qualquer mudança na entidade afeta o contrato da API.
- **`buscarPorSearch` tem 3 estágios**: 1) busca exata por telefone, 2) `findByTelefoneContaining`, 3) `findByNomeContainingIgnoreCase` — com `distinct()` no final. Isso pode gerar muitas queries.
- **`listarPaginado` duplica lógica de busca**: A busca textual existe tanto em `listarTodos`/`buscarPorSearch` (sem paginação) quanto em `listarPaginado` (com paginação), com implementações diferentes.
- **`ClienteAuthService` importa de `auth.config`**: Acesso direto a `JwtTokenProvider` de outro módulo. Como `auth` é fundacional, o acoplamento é tolerável, mas idealmente deveria ser via um service no módulo `auth`.
- **Token cliente com papel "CLIENTE"**: Não existe no enum `Papel`, então o `SecurityConfig` não consegue usar `hasRole("CLIENTE")`. Rotas de e-commerce são protegidas por `requestMatchers` públicos + autenticação via `authenticated()`.
