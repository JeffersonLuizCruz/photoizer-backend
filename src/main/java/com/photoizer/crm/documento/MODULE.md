# Módulo: Documento

## 1. Responsabilidade
Geração de documentos como contratos e recibos em PDF, além de servir comprovantes de pagamento. Reage a eventos de confirmação de agendamento para gerar contrato automaticamente.

## 2. Estrutura
```
documento/
├── service/
│   ├── ContratoService.java        # Geração de contrato + recibo (marca contratoGerado=true no agendamento)
│   └── PdfGeneratorService.java    # Stub: retorna byte[0] (implementação real pendente)
├── api/
│   └── DocumentoController.java    # GET /contratos/{id}, GET /recibos/{id}, GET /comprovantes/{id}/{tipo}
└── listener/
    └── DocumentoEventListener.java # Gera contrato automaticamente ao receber AgendamentoConfirmadoEvent
```

## 3. Dependências Externas

### Módulos internos
| Módulo | Uso |
|--------|-----|
| **agenda** | `AgendamentoRepository`, `Agendamento`, `AgendamentoConfirmadoEvent`, `AgendamentoNaoEncontradoException` |
| **shared** | `FileStorageService` |

### Eventos consumidos
| Evento | Ação |
|--------|------|
| `AgendamentoConfirmadoEvent` | Gera contrato automaticamente via `ContratoService.gerarContrato()` |

## 4. Fluxos Principais

### Fluxo 1: Geração Automática de Contrato
1. Módulo `agenda` publica `AgendamentoConfirmadoEvent`
2. `DocumentoEventListener.handleAgendamentoConfirmado()`:
   - Chama `ContratoService.gerarContrato(agendamentoId)`
   - Busca agendamento no banco
   - Seta `contratoGerado = true` no agendamento
   - Chama `PdfGeneratorService.gerarContrato(agendamento)`

### Fluxo 2: Download de Documentos
- `GET /api/v1/documentos/contratos/{agendamentoId}` → gera e baixa contrato PDF
- `GET /api/v1/documentos/recibos/{agendamentoId}` → gera e baixa recibo PDF
- `GET /api/v1/documentos/comprovantes/{agendamentoId}/{tipo}` → serve comprovante de pagamento (entrada/final) do filesystem

## 5. Regras Específicas
1. **PdfGeneratorService é stub**: `gerarContrato()` e `gerarRecibo()` apenas logam e retornam `new byte[0]`. A implementação real de geração de PDF não foi feita.
2. **Controller também injeta `FileStorageService`**: Embora não use diretamente no código atual, está na injeção de dependência.
3. **`contratoGerado` boolean**: O agendamento tem um campo booleano que é setado como `true` quando o contrato é gerado. Não há verificação se já foi gerado antes — contrato pode ser gerado múltiplas vezes.

## 6. Testes
Nenhum teste específico para este módulo.

## 7. Pontos de Atenção
- **PDF generator não implementado**: `PdfGeneratorService` retorna array vazio. Qualquer endpoint de contrato/recibo retornará PDF vazio (0 bytes).
- **`contratoGerado` sem verificação**: O `ContratoService.gerarContrato()` não verifica se o contrato já foi gerado, permitindo geração redundante.
- **Controller tem `FileStorageService` não usado**: Injetado mas não utilizado no controller atual.
