# Módulo: Despesa

## 1. Responsabilidade
Gerencia despesas manuais do estúdio (manutenção, compras, etc.), vínculo com agendamentos/fotógrafos, categorias de despesa e despesas recorrentes (job agendado). Consumido pelo módulo `dashboard`/`financeiro` para indicadores financeiros e pelos relatórios de fotógrafo (custos por fotógrafo).

## 2. Estrutura
```
despesa/
├── model/
│   ├── Despesa.java            # Entidade (extends BaseEntity): descricao, valor, categoria (String + categoriaRef @ManyToOne), data, formaPagamento, status, recorrencia, geradaDeId, agendamentoId, fotografoId, dataPagamento, urlComprovante, observacao
│   ├── DespesaCategoria.java   # Entidade (extends BaseEntity): nome (unique), cor, ativo, ordem
│   ├── StatusDespesa.java      # Enum: PAGO, PENDENTE, RECORRENTE
│   └── RecorrenciaDespesa.java # Enum: UNICA, MENSAL, ANUAL
├── repository/
│   ├── DespesaRepository.java          # JpaRepository + JpaSpecificationExecutor + findBy* (período, agendamento, fotografo, status+dataProximaGeracao, countByCategoriaRefId)
│   └── DespesaCategoriaRepository.java # JpaRepository + findByNomeIgnoreCase, findByAtivoTrueOrderByOrdemAscNomeAsc
├── service/
│   └── DespesaService.java     # 312 linhas: CRUD, categorias, vínculos, marcação de pago, comprovante, recorrentes + @Scheduled
└── api/
    ├── DespesaController.java  # ~153 linhas: CRUD, filtros/ordenação, pagar, vínculos, comprovante, categorias
    ├── DespesaRequest.java     # Record @Valid: descricao, valor, categoriaId, data, formaPagamento, status, recorrencia, agendamentoId, fotografoId, observacao
    ├── DespesaResponse.java    # Record + static of(): todos os campos + categoria aninhada
    ├── DespesaCategoriaRequest.java    # Record: nome, cor, ativo, ordem
    ├── DespesaCategoriaResponse.java   # Record + static of(): nome, cor, ativo, ordem + contagem
    ├── DespesaAgendamentoRequest.java  # Record: agendamentoId
    └── DespesaFotografoRequest.java    # Record: fotografoId
```

## 3. Dependências Externas

### Módulos internos importados
| Módulo | Uso |
|--------|-----|
| **agenda** | `AgendamentoRepository` — validar existência do trabalho (agendamento) antes de vincular despesa |
| **shared** | `BaseEntity` (herança), `FormaPagamento`, `FileStorageService` (comprovante) |

> A validação de existência de agendamento (`existsById`) é legítima, mas deveria ser via facade/evento do módulo `agenda` em vez de injetar o repositório. (Ver 7.4.)

### Módulos que dependem deste
| Módulo | Uso |
|--------|-----|
| **dashboard** | `DespesaRepository` p/ despesas mensais e saldo líquido |
| **fotografo** | `DespesaRepository` p/ custos por fotógrafo (`sumPorFotografo`, `sumPorCategoria`) |
| **agenda** | cálculo de custos de ensaio (somad de despesas do agendamento) |

### Eventos
Não publica nem consome eventos. (Candidato a emitir `DespesaRegistradaEvent`/`DespesaPagaEvent` para financeiro/dashboard, evitando acoplamento direto.)

## 4. Fluxos Principais

### Fluxo 1: CRUD de Despesas
1. `GET /api/v1/despesas` (8 filtros: data, categoria, status, agendamento, fotógrafo, ordenação) → `listar()` com `Specification<Despesa>` (`DespesaService.java:47-67`).
2. `POST` → `criar()` (`:75-103`): valida categoria, defaults (`status=PENDENTE`, `recorrencia=UNICA`), valida agendamento se informado, calcula `dataProximaGeracao` se `RECORRENTE`.
3. `PUT` → `atualizar()` (`:105-135`): merge manual de todos os campos; defaults divergem de `criar` (sempre seta PENDENTE/UNICA se nulo).
4. `DELETE` → `remover()`.

### Fluxo 2: Vínculos e Pagamento
- `PATCH /{id}/agendamento` e `/fotografo` → vincula/desvincula (`:144-157`).
- `PATCH /{id}/pagar` → `marcarComoPaga()` (`:173-181`): bloqueia `RECORRENTE` (deve marcar a ocorrência gerada); seta `PAGO` + `dataPagamento`.
- `POST /{id}/comprovante` → `anexarComprovante()` (`:183-188`): salva via `FileStorageService` (sem validação de tipo/extensão).

### Fluxo 3: Recorrências (Job)
1. `@Scheduled(cron = "0 5 6 * * *")` → `gerarDespesasRecorrentes()` (`:201-229`): para cada `RECORRENTE` com `dataProximaGeracao <= hoje`, cria ocorrência `PENDENTE`/`UNICA` com `geradaDeId` e avança a data de geração.
2. `GET /recorrentes-proximas` → `recorrentesProximas(dias)` (`:190-199`): lista RECORRENTE vencendo nos próximos dias.

### Fluxo 4: Categorias
- CRUD + `removerCategoria()` (`:291-301`): se houver despesas vinculadas, **inativa** em vez de deletar (soft-delete inteligente).

### Fluxo 5: Consultas financeiras
- `somarCustosFotografo`/`somarCustosTodosFotografos` (`:159-171`): **soma em memória** (lê lista e reduz).
- `somarDespesasNoPeriodo` (`:307-311`): **soma em memória**.

## 5. Regras Específicas
1. **Status usa enum** (`StatusDespesa`), diferente de outros módulos que usam String — consistente neste módulo.
2. **Despesa carrega `categoria` (String) redundante + `categoriaRef`**: `categoria` é nome denormalizado usado como fallback (`DespesaResponse` usa `categoriaRef` se possível).
3. **Merge manual completo** em `atualizar` (`:116-126`) — candidato a MapStruct.
4. **`removerCategoria` com soft-delete parcial**: se há despesas, inativa; senão deleta (comportamento duplo).
5. **`gerarDespesasRecorrentes` não transacional por origem**: `@Transactional` na classe; cada save é individual — sem idempotência explícita (se o job rodar 2× no mesmo dia reprocessa? Depende de `dataProximaGeracao`).

## 6. Testes
Nenhum teste específico para este módulo. Apenas `CrmApplicationTests` (smoke de contexto).

## 7. Dívidas Técnicas e Melhorias Recomendadas

### 7.1 Exceções genéricas `IllegalArgumentException` — **P1**
- `buscarPorId`, `criar`, `atualizar`, `remover`, `vincular*`, `marcarComoPaga`, `resolverCategoria`, categorias — **todas as exceções** são `IllegalArgumentException` (`DespesaService.java:72, 81, 109, 139, 147, 176, 241, 244, 266, 281, 293`) → mapeadas para 422 genérico.
- **Solução**: hierarquia central `BusinessException`/`NotFoundException`; separar "não encontrado" (404) de "regra de negócio" (409/422).

### 7.2 Soma de valores em memória — **P2**
- `somarCustosFotografo`, `somarCustosTodosFotografos` (`:159-171`), `somarDespesasNoPeriodo` (`:307-311`) carregam a lista inteira e reduzem com Java Streams.
- **Solução**: `SELECT SUM(...)` no repositório (ou `JpaSpecificationExecutor` com aggregação).

### 7.3 Duplicação `/recorrentes-proximas` e `gerarDespesasRecorrentes` — **P2**
- Ambas usam a mesma query `findByStatusAndDataProximaGeracaoNotNullAndDataProximaGeracaoLessThanEqual` com limites distintos.
- **Solução**: método único parametrizado `findRecorrentesVencidas(LocalDate limite)`.

### 7.4 Acoplamento a `AgendamentoRepository` — **P2**
- `criar`/`atualizar`/`vincularAgendamento` validam existência via repo da agenda (`:80, 109, 146`).
- **Solução**: facade/ACL do módulo agenda (`agenda.port.AgendamentoGateway`) ou evento; evitar importação direta.

### 7.5 Merge manual — **P2**
- `atualizar()` seta 10 campos à mão (`:116-126`); defaults repetidos entre `criar`/`atualizar`.
- **Solução**: MapStruct (`@Mapping(target="categoria", ignore=true)` + custom) e método de domínio `aplicar(AtualizarDespesa)`.

### 7.6 Herança `BaseEntity` → composição — **P1** (padrão-aplicável)
- `Despesa`, `DespesaCategoria` estendem `@MappedSuperclass`.
- **Solução**: `@Embeddable AuditInfo` + Auditing; eliminar `BaseEntity`/`@SuperBuilder`.

### 7.7 Job de recorrência sem proteções — **P3**
- `@Scheduled` singletone instance; sem lock (`ShedLock`/DB) — com múltiplas instâncias duplicaria; sem idempotência por dia.
- **Solução**: idempotência (verificar `geradaDeId` existente) ou lock distribuído; assim que houver multi-instância.

### 7.8 Comprovante sem validação — **P3**
- `anexarComprovante` salva qualquer `MultipartFile` (`:183-188`) sem whitelist de tipo/tamanho.
- **Solução**: validar extensão/MIME + tamanho (padrão `shared/storage` fase 2).

### 7.9 Ordenação com whitelist manual — **P3**
- Whitelist de colunas de sort em string (`:62-63`) — frágil e repetida.
- **Solução**: enum `DespesaSortBy` ou `Sort` tipado.

### 7.10 DTOs manuais — **P3**
- `DespesaResponse.of`/`DespesaCategoriaResponse.of` manuais.
- **Solução**: MapStruct (decisão aprovada).

## 8. Exemplos de arquivos afetados
- `DespesaService.java:47-67` — `Specification` com filtros; `:159-171, :307-311` — soma em memória; `:201-229` — job recorrente; `:116-126` — merge manual; `:72,139,176` — `IllegalArgumentException`.
- `DespesaRepository.java:12-26` — queries por período/status; falta `SUM`.
- `DespesaController.java:40-53` — 8 filtros no controller (sem DTO de filtro).
- `Despesa.java:31-93` — entidade com `categoria` String duplicada + `categoriaRef`.