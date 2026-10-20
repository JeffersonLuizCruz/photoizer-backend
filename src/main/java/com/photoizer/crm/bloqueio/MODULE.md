# Módulo: Bloqueio

## 1. Responsabilidade
Gerencia compromissos/indisponibilidades criados pelo staff que impedem novos agendamentos em um intervalo de tempo. Um bloqueio pode ser **global** (toda a equipe) ou restrito a um **profissional**. É consumido pelo módulo `agenda` para compor a disponibilidade e validar conflitos na criação, edição, reagendamento e aprovação de propostas.

## 2. Estrutura Interna
```
bloqueio/
├── model/
│   └── BloqueioAgenda.java              # Entidade JPA: titulo, descricao, inicio, fim, profissionalId, criadoPorId, ativo
├── repository/
│   └── BloqueioAgendaRepository.java    # Consultas de sobreposição (todas / aplicáveis ao profissional)
├── service/
│   ├── BloqueioAgendaCommand.java       # Record de entrada (criar/editar)
│   └── BloqueioAgendaService.java       # CRUD + validações de período, escopo, conflito e ownership
├── api/
│   ├── BloqueioAgendaController.java    # REST /api/v1/bloqueios
│   ├── BloqueioAgendaRequest.java       # Record @Valid
│   ├── BloqueioAgendaResponse.java      # Record de saída
│   └── BloqueioAgendaMapper.java        # MapStruct
├── acl/
│   └── BloqueioAgendaPortAdapter.java   # Implementa shared.port.BloqueioAgendaPort (consumido pela agenda)
└── exception/
    ├── BloqueioNaoEncontradoException.java
    ├── PeriodoInvalidoException.java
    ├── PeriodoJaBloqueadoException.java
    └── AgendamentoNoPeriodoException.java
```

## 3. Dependências Externas

### Portas consumidas (shared/port)
| Porta | Implementação | Uso |
|-------|---------------|-----|
| `AgendamentoOcupacaoPort` | `agenda/adapter/AgendamentoOcupacaoAdapter` | Rejeitar bloqueio sobre ensaio ativo |
| `UsuarioStaffPort` | `auth/adapter/UsuarioStaffPortAdapter` | Validar profissional ativo |

### Portas expostas
| Porta | Consumidor |
|-------|------------|
| `BloqueioAgendaPort` | `agenda/service/DisponibilidadeService` |

### Eventos
Nenhum.

## 4. Endpoints
| Método | Rota | Papéis | Regra |
|--------|------|--------|-------|
| POST | `/api/v1/bloqueios` | ADMIN, FOTOGRAFO, EDITOR, AGENDADOR | Cria compromisso |
| GET | `/api/v1/bloqueios?dataInicio&dataFim&profissionalId` | todos staff | Lista no período; sem filtro retorna todos |
| GET | `/api/v1/bloqueios/{id}` | todos staff | Detalhe |
| PUT | `/api/v1/bloqueios/{id}` | criador ou ADMIN | Atualiza |
| DELETE | `/api/v1/bloqueios/{id}` | criador ou ADMIN | Cancelamento lógico (`ativo=false`) |

## 5. Regras Específicas
1. **Escopo**: `profissionalId` nulo = global; preenchido = apenas o profissional.
2. **Intervalo semiaberto** `[inicio, fim)`. "Dia inteiro" é representado por `00:00` até `00:00` do dia seguinte.
3. **Período máximo** de 90 dias; `inicio < fim` obrigatório.
4. **Conflito com ensaio**: rejeita (409) se houver ensaio ativo no período. A lista de status que ocupam a agenda é a mesma de `DisponibilidadeService.STATUS_IGNORADOS` (`CONFIRMADO`, `REALIZADO`, `AGUARDANDO_PAGAMENTO_FINAL`, `EM_EDICAO`).
5. **Sobreposição entre bloqueios**: rejeita (409) quando outro bloqueio aplicável ao mesmo escopo já cobre o período.
6. **Ownership**: edição/cancelamento somente pelo `criadoPorId` ou ADMIN (403).
7. **Cancelamento lógico**: preserva histórico e libera a agenda.

## 6. Integração com a Agenda
- `DisponibilidadeService.verificarDisponibilidade` inclui `bloqueios` no `DisponibilidadeResponse` e considera `disponivel=false` quando há bloqueio aplicável.
- `DisponibilidadeService.validarConflitoAgenda` lança `ConflitoDeAgendaException` (409) quando o período está bloqueado. Isso cobre proposta, edição, reagendamento e aprovação.
- Limitação V1: o conflito considera o **fotógrafo principal** do agendamento; parceiros em `agendamento_fotografos` não são avaliados.

## 7. Banco de Dados
Migração `V5__create_bloqueios_agenda.sql`:
- FKs para `users` (`profissional_id`, `criado_por_id`, `cancelado_por_id`);
- `check (fim > inicio)`;
- índices parciais em `(inicio, fim)` e `(profissional_id, inicio, fim)` com `ativo = true`.

## 8. Testes
- `BloqueioAgendaServiceTest`: criação, período inválido, profissional inativo, sobreposição com bloqueio/ensaio, ownership e cancelamento lógico.
- `DisponibilidadeServiceTest`: bloqueio global, conflito na validação e compatibilidade com a regra de status.
