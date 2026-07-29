# Módulo: Despesa

## 1. Responsabilidade
Gerencia despesas manuais do estúdio (manutenção, compras, etc.). Usado pelo módulo `dashboard` para calcular indicadores financeiros (despesas mensais, saldo líquido).

## 2. Estrutura
```
despesa/
├── model/
│   └── Despesa.java            # Entidade JPA (extends BaseEntity, @SuperBuilder)
├── repository/
│   └── DespesaRepository.java  # JpaRepository + busca por período
├── service/
│   └── DespesaService.java     # CRUD completo (listar, buscar, criar, atualizar, remover)
└── api/
    ├── DespesaController.java  # CRUD REST + filtro por data
    ├── DespesaRequest.java     # Record: descricao, valor, categoria, data, observacao
    └── DespesaResponse.java    # Record: id, descricao, valor, categoria, data, observacao, createdAt
```

## 3. Dependências Externas

### Módulos internos
- **shared** → `BaseEntity`

### Módulos que dependem deste
| Módulo | Uso |
|--------|-----|
| **dashboard** | `DespesaRepository` para calcular despesas mensais no financeiro |

### Eventos
Nenhum. Módulo não publica nem consome eventos.

## 4. Fluxos Principais

### Fluxo 1: CRUD de Despesas
- `GET /api/v1/despesas?dataInicio=&dataFim=` → lista com filtro opcional por período
- `GET /api/v1/despesas/{id}` → busca por ID
- `POST /api/v1/despesas` → cria nova despesa (recebe `DespesaRequest`)
- `PUT /api/v1/despesas/{id}` → atualiza despesa existente
- `DELETE /api/v1/despesas/{id}` → remove despesa

### Fluxo 2: Consulta pelo Dashboard
- `DashboardService.calcularFinanceiroMensal()` chama `despesaRepository.findByDataBetweenOrderByDataDesc()` para obter despesas do período
- As despesas são agregadas por mês e somadas ao total de despesas (deslocamento + comissão + despesas manuais)

## 5. Regras Específicas
1. **CRUD padrão**: Sem regras complexas de negócio. Apenas validação de existência antes de remover/atualizar.
2. **`categoria` como String**: Não usa enum. Aceita qualquer valor textual — sem padronização de categorias.
3. **Uso de `RuntimeException` genérica**: `DespesaService.buscarPorId()` e `remover()` lançam `new RuntimeException(...)` em vez de uma exceção de domínio específica.
4. **Service com `@Transactional` em nível de classe**: Métodos `listar` e `buscarPorId` sobrescrevem com `readOnly = true`.

## 6. Testes
Nenhum teste específico para este módulo.

## 7. Pontos de Atenção
- **Exception genérica**: Diferente dos outros módulos (que têm exceções específicas como `ClienteNaoEncontradoException`), `DespesaService` usa `RuntimeException`. Isso dificulta tratamento diferenciado no `GlobalExceptionHandler`.
- **`categoria` sem enum**: Categorias podem ser digitadas incorretamente ou com variações (ex: "manutenção" vs "Manutenção"). Idealmente deveria ser um enum ou uma tabela de categorias.
- **Sem endpoint de `PATCH`**: Apenas `PUT` (substituição completa). Todos os campos são obrigatórios na atualização.
- **DespesaRequest não valida categoria**: Não há `@NotBlank` ou enum validation na categoria no request (a validação está na entidade com `@NotBlank`).
