# Módulo: E-commerce

## 1. Responsabilidade
Gerencia a galeria pública do cliente, compra de fotos extras, pedidos de pacotes, cupons de desconto, avaliações e administração de vendas. É o maior módulo do sistema (~2.4k linhas, 9 entidades, 9 controllers).

## 2. Estrutura
```
ecommerce/
├── model/
│   ├── CompraExtra.java         # Entidade JPA (extends BaseEntity): agendamentoId, valorTotal, quantidadeFotos, metodoPagamento, status, urlComprovante, dataPagamento
│   ├── StatusCompraExtra.java   # Enum: AGUARDANDO_COMPROVANTE, AGUARDANDO_CONFIRMACAO, PAGA, CANCELADA
│   ├── MetodoPagamento.java     # Enum: PIX, CREDITO, DEBITO, BOLETO, DINHEIRO
│   ├── Pedido.java              # Entidade JPA (extends BaseEntity): pacoteId, fotosSelecionadasIds CSV, fotosExtrasIds CSV, calculos financeiros, status String
│   ├── Cupom.java               # Entidade JPA: codigo, tipoDesconto(PERCENTUAL/FIXO), valorDesconto, usoLimite, usosAtuais, valorMinimoPedido, ativo, dataValidade
│   ├── ItemCarrinho.java        # Entidade JPA: sessionId, fotoId, agendamentoId
│   ├── Favorito.java            # Entidade JPA: sessionId, fotoId, agendamentoId (wishlist)
│   ├── Sessao.java              # Entidade JPA: sessionId, tokenGaleria, createdAt
│   └── Avaliacao.java           # Entidade JPA: agendamentoId, clienteId, nota, comentario
├── repository/
│   ├── CompraExtraRepository.java  # JpaRepository + queries por status, período, agendamentoId
│   ├── PedidoRepository.java       # JpaRepository
│   ├── CupomRepository.java        # JpaRepository + findByCodigoIgnoreCase
│   ├── ItemCarrinhoRepository.java # JpaRepository + delete/contagem por sessionId+agendamentoId
│   ├── FavoritoRepository.java     # JpaRepository + findBySessionIdAndFotoId
│   ├── AvaliacaoRepository.java    # JpaRepository
│   └── SessaoRepository.java       # JpaRepository
├── service/
│   ├── EcommerceService.java    # 458 linhas: galeria, carrinho, checkout, comprovante, favoritos, download, admin
│   └── PedidoService.java       # 158 linhas: criação de pedidos com cálculos + cupom
├── api/ (9 controllers, 30 DTOs)
│   ├── EcommerceController.java         # Galeria pública + carrinho + checkout + favoritos + download
│   ├── AdminEcommerceController.java    # Admin: regerar token, override seleção
│   ├── AdminComprasController.java      # Admin: listar/detalhar/cancelar compras
│   ├── AdminPedidoController.java       # Admin: atualizar status pedido
│   ├── AdminAnalyticsController.java    # Admin: relatório de compras
│   ├── CupomController.java             # CRUD de cupons + validação
│   ├── SessaoController.java            # Criar sessão para galeria
│   ├── PedidoController.java            # Criar pedido (cliente)
│   ├── AvaliacaoController.java         # CRUD de avaliações
│   └── (30 DTOs: records Request/Response)
├── event/
│   ├── CompraExtraCriadaEvent.java      # Publicado no checkout
│   └── CompraExtraConfirmadaEvent.java  # Publicado ao confirmar pagamento
├── listener/
│   └── EcommerceEventListener.java      # Reage aos próprios eventos + notifica
└── exception/
    └── TokenExpiradoException.java      # Token da galeria expirado
```

## 3. Dependências Externas

### Módulos internos (importados diretamente — alguns violam Modulith)
| Módulo | Uso |
|--------|-----|
| **agenda** | `AgendamentoRepository`, `Agendamento` (busca por token, atualiza valorExtras) |
| **foto** | `FotoEnsaioRepository`, `FotoEnsaio`, `StatusFoto`, `FotoEnsaioResponse`, `FotoService`, `ImageProcessingService` |
| **config** | `ConfiguracaoService` (valorUnitarioFotoExtra) |
| **notificacao** | `NotificacaoService` (notificar nova compra, notificar confirmação) — usado no listener |
| **pacote** | `PacoteRepository` (usado em PedidoService) |
| **shared** | `FileStorageService`, `BaseEntity` |

### Eventos publicados
| Evento | Consumidores |
|--------|-------------|
| `CompraExtraCriadaEvent` | `EcommerceEventListener` (notifica admin) |
| `CompraExtraConfirmadaEvent` | `EcommerceEventListener` (atualiza valorExtras no agendamento + notifica) |

### Eventos consumidos
Nenhum. O módulo escuta apenas seus próprios eventos.

## 4. Fluxos Principais

### Fluxo 1: Galeria Pública (Cliente)
1. `GET /api/v1/ecommerce/galeria/{token}` → busca agendamento por token + fotos publicadas
2. `PATCH /galeria/{token}/selecionar` → marca fotos como selecionadas para o pacote
3. `POST /galeria/{token}/carrinho` → adiciona foto ao carrinho (sessão via `X-Session-Id`)
4. `GET /galeria/{token}/calcular` → calcula valor do carrinho (quantidade × valorUnitario)
5. `POST /galeria/{token}/checkout` → cria `CompraExtra` com status `AGUARDANDO_COMPROVANTE`, limpa carrinho, publica `CompraExtraCriadaEvent`
6. `POST /galeria/{token}/comprovante` → upload do comprovante de pagamento, status → `AGUARDANDO_CONFIRMACAO`
7. `PATCH /admin/compras/{id}/confirmar` (admin) → status → `PAGA`, fotos → `PAGA`, publica `CompraExtraConfirmadaEvent`

### Fluxo 2: Download de Fotos
1. `GET /galeria/{token}/download/{fotoId}` → download de foto individual (só se `selecionadaPacote` ou `status == PAGA`)
2. `GET /galeria/{token}/download-zip` → gera ZIP com todas as fotos liberadas (usa temp directory)

### Fluxo 3: Pedido de Pacote
1. `POST /api/v1/ecommerce/pedidos` → `PedidoService.criar(clienteId, request)`:
   - Valida pacote ativo e quantidade de fotos selecionadas
   - Calcula: subtotalPacote, subtotalExtras (quantidade × precoFotoExtra), taxaEntrega
   - Aplica cupom se informado (valida: ativo, data, limite uso, valor mínimo)
   - Cria `Pedido` com status `AGUARDANDO_PAGAMENTO` e `tokenGaleria = UUID.randomUUID()`
2. `PUT /admin/pedidos/{id}/status` (admin) → atualiza status, seta `dataConclusao` se CONCLUIDO
3. `POST /pedidos/{id}/cancelar` → cancela (exceto se PAGO ou CONCLUIDO)

### Fluxo 4: Wishlist (Favoritos)
- `POST /galeria/{token}/favoritos/{fotoId}` → adiciona aos favoritos da sessão
- `DELETE /galeria/{token}/favoritos/{fotoId}` → remove
- `GET /galeria/{token}/favoritos` → lista IDs das fotos favoritas

## 5. Regras Específicas
1. **Galeria pública via token UUID com expiração (15 dias)**: Token gerado pelo módulo `agenda` na criação do agendamento. Se expirado, lança `TokenExpiradoException`.
2. **Sessão via header `X-Session-Id`**: Carrinho e favoritos são vinculados a sessionId (UUID gerado pelo frontend), não a usuário autenticado.
3. **Upload de comprovante**: MultipartFile validado no controller. Comprovante salvo em `uploads/{agendamentoId}/comprovante_extra/`.
4. **`Pedido.status` como String**: Valores esperados: `AGUARDANDO_PAGAMENTO`, `PAGO`, `CONCLUIDO`, `CANCELADO`. Diferente de `StatusCompraExtra` que é enum.
5. **Fotos selecionadas como CSV**: `Pedido.fotosSelecionadasIds` e `fotosExtrasIds` armazenadas como string CSV de UUIDs — não é normalizado.
6. **Preço de foto extra**: Lido de `ConfiguracaoService.getValorDecimal("valorUnitarioFotoExtra")` com default R$ 15,00.
7. **`confirmarPagamento` atualiza status de fotos em memória**: `fotoEnsaioRepository.findAll().stream().filter(...)` em vez de query no repositório.

## 6. Testes
Nenhum teste específico para este módulo.

## 7. Pontos de Atenção
- **`Pedido.status` como String**: Propenso a erros de digitação. `StatusCompraExtra` é enum, mas `Pedido` usa String — inconsistência no mesmo módulo.
- **`confirmarPagamento` e `cancelarCompra` fazem `findAll()`**: `fotoEnsaioRepository.findAll().stream().filter(...)` para encontrar fotos por `compraExtraId`. Deveria ser query no repositório.
- **`Pedido.fotosSelecionadasIds` como CSV**: UUIDs concatenados com vírgula. Para consultar fotos individualmente, é necessário split e parse. Sem integridade referencial.
- **Controller `EcommerceController` injeta `FotoService`**: Dependência direta do módulo `foto` (além dos repositórios já importados). O controller monta `CarrinhoResponse` chamando `fotoService.buscarPorId()` para cada item.
- **`Sessao` entity existe mas `EcommerceController` não a usa**: A sessão é integralmente gerenciada pelo header `X-Session-Id` no frontend. `SessaoController` e `Sessao` entity parecem não ser utilizados pelo fluxo principal.
- **`Cupom.usosAtuais` não é thread-safe**: Se dois pedidos simultâneos usarem o mesmo cupom, ambos podem passar a validação antes do save.
- **`AdminComprasRelatorioResponse.totalAguardando`**: Soma `AGUARDANDO_COMPROVANTE` + `AGUARDANDO_CONFIRMACAO` — duplica chamadas ao repositório.
