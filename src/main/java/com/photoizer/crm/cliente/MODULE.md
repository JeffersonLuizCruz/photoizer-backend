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
│   ├── ClienteRepository.java # JpaRepository + JpaSpecificationExecutor
│   └── ClienteSpecification.java # Specification para busca unificada
├── service/
│   ├── ClienteService.java     # CRUD + busca com Specification
│   └── ClienteAuthService.java # Registro/login/atualizarPerfil do cliente + geração de JWT
├── api/
│   ├── ClienteController.java      # CRUD (admin)
│   ├── ClienteAuthController.java  # POST /registro, /login, GET/PUT /perfil
│   └── dto/
│       ├── ClienteResponse.java        # DTO de resposta (sem senhaHash)
│       ├── ClienteAdminResponse.java   # DTO de resposta admin (com auditoria)
│       ├── CriarClienteRequest.java    # DTO de requisição para criação
│       ├── AtualizarClienteRequest.java # DTO de requisição para atualização
│       ├── ClienteMapper.java          # Mapper para conversão entidade ↔ DTO
│       ├── ClienteRegistroRequest.java # Record: nome, email, telefone, senha, preferencias
│       ├── ClienteLoginRequest.java    # Record: email, senha
│       ├── ClienteAuthResponse.java    # Record: token, id, nome, email, telefone
│       └── AtualizarPerfilRequest.java # Record: nome, telefone, email, cpf, cidade, estado
└── exception/
    ├── ClienteNaoEncontradoException.java # RuntimeException com UUID
    └── ClienteDuplicadoException.java     # RuntimeException para duplicatas
```

## 3. Dependências Externas

### Módulos internos importados
- **shared** → `BaseEntity`, `PageResponse`, `TokenService` (abstração para geração de JWT).

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
- `POST /api/v1/clientes` → recebe `CriarClienteRequest`, retorna `ClienteAdminResponse`.
- `GET /api/v1/clientes` → paginado com busca (`PageResponse<ClienteAdminResponse>`).
- `PUT /api/v1/clientes/{id}` → recebe `AtualizarClienteRequest`, retorna `ClienteAdminResponse`.
- `DELETE /api/v1/clientes/{id}` → `ClienteNaoEncontradoException` se ausente.

### Fluxo 2: Autenticação Cliente (E-commerce)
- `POST /api/v1/auth/cliente/registro` (público): valida email/telefone únicos; cria `Cliente` com `senhaHash` BCrypt, `origem=OUTROS`, `dataCadastro=now` (via `@PrePersist`); gera JWT com papel **"CLIENTE"**.
- `POST /api/v1/auth/cliente/login` (público): busca por email (case-insensitive), valida BCrypt, gera token.
- `GET/PUT /api/v1/auth/cliente/perfil`: resolve `userId` do token (`@AuthenticationPrincipal String userId`) e opera no cliente. Retorna `ClienteResponse` (sem senhaHash).

### Fluxo 3: Criação de Cliente via Agendamento
- `AgendamentoService.criarAgendamento()`: `clienteId` → busca existente; senão telefone; senão cria novo `Cliente` **sem** `senhaHash`/`dataCadastro`.

## 5. Regras Específicas
1. **API nunca expõe entidade JPA** - Usa DTOs (`ClienteResponse`, `ClienteAdminResponse`) que excluem `senhaHash` e dados sensíveis.
2. **`dataCadastro` automático** - Definido via `@PrePersist` na entidade `Cliente`, garantindo consistência em todos os fluxos.
3. **Telefone único** (`Cliente.java:41`) com restrição duplicada (coluna `unique = true` + `@UniqueConstraint` da tabela, `:25`).
4. **Encapsulamento de estado** - `@Setter(AccessLevel.PRIVATE)` impede sobrescrita externa de campos sensíveis. Métodos de domínio (`atualizarDados`, `atualizarPerfil`, `definirSenhaHash`) controlam alterações.
5. **Busca unificada** - `ClienteSpecification` elimina queries duplicadas e `distinct()` em memória.

## 6. Testes
Nenhum teste específico.

## 7. Dívidas Técnicas Resolvidas

### 7.1 Contrato da API acoplado à entidade JPA — **[RESOLVIDO]**
- Controllers agora usam DTOs (`ClienteRequest`, `ClienteResponse`) em vez de entidades JPA.
- `ClienteMapper` centraliza conversão entidade ↔ DTO.

### 7.2 Vazamento do hash de senha — **[RESOLVIDO]**
- `ClienteResponse` e `ClienteAdminResponse` não incluem `senhaHash`.
- `@Setter(AccessLevel.PRIVATE)` impede acesso externo ao campo.

### 7.3 `dataCadastro` inconsistente — **[RESOLVIDO]**
- `@PrePersist` na entidade `Cliente` define `dataCadastro = LocalDateTime.now()` automaticamente.

### 7.4 Busca fragmentada e N queries — **[RESOLVIDO]**
- `ClienteSpecification.buscarPorNomeOuTelefone()` unifica busca com Specification pattern.
- `ClienteRepository` estende `JpaSpecificationExecutor`.

### 7.5 Violações Modulith — **[RESOLVIDO]**
- Dependência `cliente` → `agenda` removida (endpoints de agendamentos movidos para módulo agenda).
- Dependência `cliente` → `auth` removida (usa `TokenService` abstração do shared).

### 7.6 Exposição da entidade → polimorfismo/DTO — **[RESOLVIDO]**
- `AgendamentoClienteResponse` movido para módulo `agenda` (era dependência cruzada).

### 7.7 `ClienteAuthService` retorna entidade — **[RESOLVIDO]**
- `atualizarPerfil` retorna entidade para uso interno, mas controller converte para `ClienteResponse`.

### 7.8 Fortuna de getters/setters expostos — **[RESOLVIDO]**
- `@Setter(AccessLevel.PRIVATE)` + métodos de domínio (`atualizarDados`, `atualizarPerfil`, `definirSenhaHash`).

## 8. Design Patterns Aplicados

### DTO Pattern
- Separa contrato da API da entidade JPA.
- `ClienteResponse`, `ClienteAdminResponse`, `CriarClienteRequest`, `AtualizarClienteRequest`.

### Mapper Pattern
- `ClienteMapper` centraliza conversão entidade ↔ DTO.
- Candidato a MapStruct quando configurado no projeto.

### Specification Pattern
- `ClienteSpecification` permite busca flexível e reutilizável.
- Substitui lógica fragmentada com `if/else` e queries duplicadas.

### Domain Model Pattern
- Métodos de domínio na entidade (`atualizarDados`, `atualizarPerfil`, `definirSenhaHash`).
- Encapsulamento de regras de negócio na entidade.

### Dependency Inversion Principle
- `TokenService` (abstração) em vez de `JwtTokenProvider` (implementação).
- Módulo cliente depende de abstração do shared, não de implementação do auth.

## 9. Próximos Passos
1. **MapStruct** - Substituir `ClienteMapper` manual por interface MapStruct.
2. **Testes** - Criar testes unitários e de integração para service e controller.
3. **Validação de CPF** - Adicionar validação de CPF válido (dígito verificador).
4. **Cache** - Considerar cache para buscas frequentes.
