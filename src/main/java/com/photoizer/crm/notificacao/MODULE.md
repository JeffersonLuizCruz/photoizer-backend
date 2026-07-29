# Módulo: Notificação

## 1. Responsabilidade
Gerencia notificações internas do sistema para usuários (admin, fotógrafos, editores). Inclui criação de notificações, marcação como lidas, disparo de lembretes de ensaio (via job agendado) e alertas de prazo de tarefas.

## 2. Estrutura
```
notificacao/
├── model/
│   └── Notificacao.java       # Entidade JPA simples (não estende BaseEntity): userId, titulo, mensagem, lida, link, createdAt
├── repository/
│   └── NotificacaoRepository.java # JpaRepository: findByUserIdOrderByCreatedAtDesc, countByUserIdAndLidaFalse
├── service/
│   └── NotificacaoService.java    # 74 linhas: criar, listar, countNaoLidas, marcarComoLida, marcarTodasComoLidas, enviarLembrete, enviarAlerta, notificarNovaCompraExtra, notificarCompraExtraConfirmada
├── api/
│   └── NotificacaoController.java # GET / (listar), GET /nao-lidas, PUT /{id}/ler, PUT /ler-todas
├── listener/
│   ├── LembreteEnsaioListener.java # Reage a AgendamentoConfirmadoEvent → envia lembrete ao cliente
│   └── AlertaPrazoListener.java    # Reage a AgendamentoConfirmadoEvent → alerta se tarefas próximas do prazo
└── scheduler/
    └── LembreteAgendamentoJob.java # Job diário (06:00) que notifica fotógrafos sobre agendamentos do dia seguinte
```

## 3. Dependências Externas

### Módulos internos
| Módulo | Uso |
|--------|-----|
| **agenda** | `AgendamentoConfirmadoEvent`, `TarefaRepository`, `StatusTarefa`, `AgendamentoRepository`, `StatusAgendamento`, `Agendamento` |
| **auth** | `UserRepository`, `Papel` |
| **config** | `ConfiguracaoService` (prazo_lembrete_ensaio_dias, prazo_alerta_edicao_dias, notificarAutomaticamente) |

### Eventos consumidos
| Evento | Ação |
|--------|------|
| `AgendamentoConfirmadoEvent` | `LembreteEnsaioListener`: envia lembrete ao cliente via log |
| `AgendamentoConfirmadoEvent` | `AlertaPrazoListener`: verifica tarefas com prazo < 1 dia e alerta |

## 4. Fluxos Principais

### Fluxo 1: Lembrete de Ensaio (via Evento)
1. Módulo `agenda` publica `AgendamentoConfirmadoEvent`
2. `LembreteEnsaioListener.handleAgendamentoConfirmado()`:
   - Chama `notificacaoService.enviarLembrete("cliente_{id}", "Seu ensaio foi confirmado...")`
   - Apenas loga — não cria notificação no banco

### Fluxo 2: Alerta de Prazo (via Evento)
1. Módulo `agenda` publica `AgendamentoConfirmadoEvent`
2. `AlertaPrazoListener.onAgendamentoConfirmado()`:
   - Busca tarefas do agendamento
   - Para cada tarefa `PENDENTE` com `dataLimite < now + 1 dia`: chama `notificacaoService.enviarAlerta()`
   - Apenas loga — não cria notificação no banco

### Fluxo 3: Job Diário de Lembretes (06:00)
1. `LembreteAgendamentoJob.enviarLembretesDiarios()`:
   - Verifica se `notificarAutomaticamente` está ativo (config)
   - Busca agendamentos de amanhã (não cancelados/noShow)
   - Agrupa por fotógrafo
   - Para cada fotógrafo: cria notificação no banco com lista de agendamentos do dia seguinte

### Fluxo 4: Gerenciamento de Notificações
- `GET /api/v1/notificacoes` → lista notificações do usuário autenticado
- `GET /api/v1/notificacoes/nao-lidas` → contagem de não lidas
- `PUT /api/v1/notificacoes/{id}/ler` → marca como lida
- `PUT /api/v1/notificacoes/ler-todas` → marca todas como lidas

## 5. Regras Específicas
1. **`Notificacao` não estende `BaseEntity`**: Usa `@Id` UUID gerado e `createdAt` manual via construtor. Não tem `updatedAt` ou `createdBy`.
2. **Notificações criadas no banco apenas pelo job**: Os listeners (`LembreteEnsaioListener`, `AlertaPrazoListener`) apenas logam — não persistem notificações. O único fluxo que realmente salva no banco é o `LembreteAgendamentoJob`.
3. **Job diário tem toggle**: A config `notificarAutomaticamente` (chave lida como BigDecimal, comparada com 1) permite desligar o job sem deploy.
4. **Controller usa `@AuthenticationPrincipal String userId`**: Extrai o userId diretamente do principal do Spring Security (que é String UUID).
5. **Métodos `enviarLembrete` e `enviarAlerta` são stubs**: Apenas logam — não enviam email, SMS ou push.

## 6. Testes
Nenhum teste específico para este módulo.

## 7. Pontos de Atenção
- **Notificações de e-commerce são apenas log**: `notificarNovaCompraExtra()` e `notificarCompraExtraConfirmada()` apenas logam, não criam notificações no banco.
- **`LembreteAgendamentoJob` usa emoji**: O título da notificação contém "📸" (emoji) — pode causar problemas de encoding em alguns terminais/banco.
- **`@AuthenticationPrincipal` depende do tipo do principal**: O `JwtAuthenticationFilter` seta `UUID.toString()` como principal. Se o tipo mudar, quebra.
- **Job faz `userRepository.findAll()`**: Para mapear fotógrafoId → nome, carrega todos os usuários em memória.
