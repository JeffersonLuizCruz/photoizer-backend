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
│   ├── CategoriaEmUsoException.java                # 409
│   └── DespesaRecorrenteNaoPagaException.java      # 422
├── model/
│   ├── Despesa.java, DespesaCategoria.java, StatusDespesa.java, RecorrenciaDespesa.java
├── repository/
│   ├── DespesaRepository.java          # + queries JPQL SUM
│   ├── DespesaCategoriaRepository.java
│   └── DespesaSpecification.java       # Specification Pattern
├── service/
│   ├── DespesaService.java             # ~230 linhas (antes 312)
│   ├── DespesaCategoriaService.java    # ~80 linhas (SRP)
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
| Custom Exception Hierarchy | Substitui IllegalArgumentException por excecoes semanticas com HTTP status correto (404/409/422) | exception/*.java |
| Specification Pattern | Encapsula filtros dinamicos e sort tipado, eliminando Specification inline no service | DespesaSpecification.java |
| Single Responsibility | Extrai CRUD de categorias para DespesaCategoriaService | DespesaCategoriaService.java |
| Facade / Port & Adapter | DespesaAgendamentoGateway (porta) + AgendamentoGatewayAdapter (adapter) eliminam acoplamento despesa->agenda.repository | DespesaAgendamentoGateway.java |
| MapStruct Mapper | Substitui mapeamento manual static of() em DTOs | DespesaMapper.java |
| DRY | Metodo buscarRecorrentesVencidas() reutilizado por recorrentesProximas() e gerarDespesasRecorrentes() | DespesaService.java |
| Query Optimization | SQL SUM com COALESCE em vez de soma em memoria via streams | DespesaRepository.java |

## 4. Dividas Tecnicas — Status

| # | Divida | Status |
|---|--------|--------|
| 7.1 | Excecoes genericas IllegalArgumentException | RESOLVIDO |
| 7.2 | Soma de valores em memoria | RESOLVIDO |
| 7.3 | Duplicacao queries recorrencia | RESOLVIDO |
| 7.4 | Acoplamento a AgendamentoRepository | RESOLVIDO |
| 7.5 | Merge manual de 10 campos | PENDENTE (P2) |
| 7.6 | Heranca BaseEntity -> composicao | PENDENTE (P1, transversal) |
| 7.7 | Job recorrencia sem protecoes | PENDENTE (P3) |
| 7.8 | Comprovante sem validacao | PENDENTE (P3) |
| 7.9 | Ordenacao com whitelist manual | RESOLVIDO |
| 7.10 | DTOs manuais static of() | RESOLVIDO |
