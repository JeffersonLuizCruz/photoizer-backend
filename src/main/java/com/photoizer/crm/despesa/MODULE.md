# Modulo: Despesa (pos-refactor)

## 1. Responsabilidade
Gerencia despesas manuais do estudio, vinculo com agendamentos/fotografos, categorias de despesa e despesas recorrentes (job agendado).

## 2. Estrutura (pos-refactor)
```
despesa/
├── exception/
│   ├── DespesaNaoEncontradaException.java         # 404
│   ├── CategoriaDespesaNaoEncontradaException.java # 404
│   ├── CategoriaDuplicadaException.java            # 409
│   ├── CategoriaEmUsoException.java                # 409 (preparada para uso futuro)
│   ├── CategoriaObrigatoriaException.java          # 422
│   ├── AgendamentoVinculadoInvalidoException.java  # 422
│   ├── DespesaRecorrenteNaoPagaException.java      # 422
│   └── StatusDespesaInvalidoException.java         # 409
├── model/
│   ├── Despesa.java, DespesaCategoria.java, RecorrenciaDespesa.java
│   └── StatusDespesa.java                          # State Pattern
├── repository/
│   ├── DespesaRepository.java          # + queries JPQL SUM + GROUP BY
│   ├── DespesaCategoriaRepository.java
│   └── DespesaSpecification.java       # Specification Pattern
├── service/
│   ├── DespesaService.java             # ~290 linhas (antes 312)
│   ├── DespesaQueryService.java        # SQL GROUP BY (antes in-memory)
│   ├── DespesaCategoriaService.java    # ~100 linhas (SRP)
│   └── DespesaAgendamentoGateway.java  # Porta/ACL
└── api/
    ├── DespesaController.java, DespesaMapper.java (MapStruct)
    └── DTOs: Request, Response, etc.
```

Adapter (modulo agenda):
```
agenda/gateway/AgendamentoGatewayAdapter.java
```

## 3. Design Patterns Aplicados

| Pattern | Motivo | Arquivo |
|---------|--------|---------|
| **State Pattern** | Centraliza transicoes de status validas no enum, eliminando validacao inline no service. Consistente com StatusCompraExtra (ecommerce) e StatusEdicao (edicao) | StatusDespesa.java |
| Custom Exception Hierarchy | Substitui IllegalArgumentException por excecoes semanticas com HTTP status correto (404/409/422) | exception/*.java |
| Specification Pattern | Encapsula filtros dinamicos e sort tipado, eliminando Specification inline no service | DespesaSpecification.java |
| Single Responsibility | Extrai CRUD de categorias para DespesaCategoriaService | DespesaCategoriaService.java |
| Facade / Port & Adapter | DespesaAgendamentoGateway (porta) + AgendamentoGatewayAdapter (adapter) eliminam acoplamento despesa->agenda.repository | DespesaAgendamentoGateway.java |
| MapStruct Mapper | Substitui mapeamento manual static of() em DTOs | DespesaMapper.java |
| Query Service Facade | DespesaQueryService agrupa despesas por mes via SQL GROUP BY, evitando que dashboard/financeiro carreguem todas as despesas em memoria | DespesaQueryService.java |
| DRY | Metodo buscarRecorrentesVencidas() reutilizado por recorrentesProximas() e gerarDespesasRecorrentes() | DespesaRepository.java |
| Query Optimization | SQL SUM com COALESCE + GROUP BY em vez de soma em memoria via streams | DespesaRepository.java |

## 4. Dividas Tecnicas — Status

| # | Divida | Status |
|---|--------|--------|
| 7.1 | Excecoes genericas IllegalArgumentException | **RESOLVIDO** (2a fase) |
| 7.2 | Soma de valores em memoria | RESOLVIDO |
| 7.3 | Duplicacao queries recorrencia | RESOLVIDO |
| 7.4 | Acoplamento a AgendamentoRepository | RESOLVIDO |
| 7.5 | Merge manual de 10 campos | PENDENTE (P2) |
| 7.6 | Heranca BaseEntity -> composicao | PENDENTE (P1, transversal) |
| 7.7 | Job recorrencia sem protecoes | PENDENTE (P3) |
| 7.8 | Comprovante sem validacao | PENDENTE (P3) |
| 7.9 | Ordenacao com whitelist manual | RESOLVIDO |
| 7.10 | DTOs manuais static of() | **RESOLVIDO** (2a fase — removidos) |
| 7.11 | StatusDespesa sem State Pattern | **RESOLVIDO** (2a fase) |
| 7.12 | DespesaQueryService agregacao em memoria | **RESOLVIDO** (2a fase — SQL GROUP BY) |
