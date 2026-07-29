# Módulo: Indicador

## 1. Responsabilidade
Gerencia o cadastro de indicadores — pessoas que indicam clientes para o estúdio e recebem comissão. Módulo auxiliar usado pelo módulo `comissao` para buscar ou criar indicadores durante o fluxo de agendamento.

## 2. Estrutura
```
indicador/
├── model/
│   └── Indicador.java          # Entidade JPA (extends BaseEntity): nome, telefone, observacoes
├── repository/
│   └── IndicadorRepository.java # JpaRepository + findByNomeAndTelefone + search (LIKE)
├── service/
│   └── IndicadorService.java   # 63 linhas: CRUD + buscarOuCriar (upsert por nome+telefone)
├── api/
│   ├── IndicadorController.java # CRUD REST (lista com total de comissões por indicador)
│   ├── IndicadorRequest.java    # Record: nome, telefone, observacoes
│   └── IndicadorResponse.java   # Record: id, nome, telefone, observacoes, totalPendente, totalPago, totalIndicacoes
└── exception/
    └── (vazio)
```

## 3. Dependências Externas

### Módulos internos
| Módulo | Uso |
|--------|-----|
| **comissao** | `IndicacaoRepository`, `Indicacao` (controller consulta comissões do indicador) |
| **shared** | `BaseEntity` |

### Módulos que dependem deste
| Módulo | Uso |
|--------|-----|
| **comissao** | `IndicacaoListener` usa `IndicadorService.buscarOuCriar()` |
| **financeiro** | `FinanceiroService.criarComissaoSeNecessario()` usa `IndicadorService.buscarOuCriar()` |

### Eventos
Nenhum.

## 4. Fluxos Principais

### Fluxo 1: CRUD de Indicadores
- `GET /api/v1/indicadores?search=` → lista com busca opcional por nome/telefone
- `GET /api/v1/indicadores/{id}` → busca por ID
- `POST /api/v1/indicadores` → cria novo indicador
- `PUT /api/v1/indicadores/{id}` → atualiza
- `DELETE /api/v1/indicadores/{id}` → remove

### Fluxo 2: `buscarOuCriar()` (usado por outros módulos)
1. Tenta encontrar por `nome` + `telefone`
2. Se não encontrar, cria novo indicador com `observacoes = null`

### Fluxo 3: Resposta com Comissões
- `IndicadorController.toResponse()` calcula para cada indicador:
  - `totalPendente`: soma das comissões com status `PENDENTE`
  - `totalPago`: soma das comissões com status `PAGA`
  - `totalIndicacoes`: quantidade de indicações
  - Tudo via `IndicacaoRepository.findByIndicadorId()` — consulta cross-module

## 5. Regras Específicas
1. **Controller consulta `IndicacaoRepository` diretamente**: Para enriquecer a resposta com totais de comissão, o controller do módulo `indicador` importa o repositório do módulo `comissao` — **viola isolamento Modulith**.
2. **`buscarOuCriar()` sem validação de duplicidade**: Se dois cadastros com mesmo nome e telefone forem criados (por concorrência), pode haver duplicatas.
3. **Exception genérica**: `IndicadorService` usa `RuntimeException` em vez de exceção de domínio.

## 6. Testes
Nenhum teste específico para este módulo.

## 7. Pontos de Atenção
- **N+1 query no `listar()`**: Para cada indicador, `toResponse()` faz uma chamada a `indicacaoRepository.findByIndicadorId()`. Se houver 100 indicadores, serão 101 queries.
- **`IndicadorRepository.search()`**: Usa `LIKE` no banco — método exato a ser verificado, mas pode ter problemas de performance com tabela grande.
- **Sem exception de domínio**: Diferente de outros módulos, não há `IndicadorNaoEncontradoException`.
