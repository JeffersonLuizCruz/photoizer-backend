# Relatório de Segurança — Loja Virtual / Fotos Extras (Photoizer CRM)

> **Data:** 2026-08-11
> **Escopo:** Regra de negócio da loja virtual — fotos do pacote são gratuitas; fotos extras devem ser pagas. Análise de vulnerabilidades que permitem ao cliente burlar a cobrança de fotos extras e baixar fotos fora do pacote.
> **Stack analisada:** Backend Spring Boot/Java (`photoizer-backend`) e Frontend React/TypeScript (`photoizer-frontend`).

---

## Resumo Executivo

A regra de negócio ("fotos do pacote são gratuitas; fotos extras devem ser pagas") **não é imposta no servidor**. O único limite de quantidade existe no frontend (`GaleriaClientePage.tsx:183`, `PhotoGrid.tsx:37`), que é trivialmente contornável via requisições diretas à API.

Além disso, existe um endpoint que entrega as fotos **originais em alta resolução** a qualquer cliente cadastrado, sem verificar pagamento nem seleção de pacote. Na prática, um cliente consegue baixar todas as fotos da galeria sem pagar nada.

**Classificação resumida:**
| ID | Severidade | Descrição | Resumo |
|----|-----------|-----------|--------|
| C1 | **Crítica** | Download de originais sem pagar (IDOR) | Endpoint `/agendamentos/.../original` liberado a qualquer autenticado + registro de cliente público |
| C2 | **Crítica** | Seleção de fotos do pacote sem limite no backend | `selecionarFotos` não valida `pacote.quantidadeFotos` |
| H1 | **Alta** | Marca d'água frágil + preview em resolução total | Watermark fraco serve imagem full-res publicamente |
| M1 | **Média** | Vazamento de URLs de originais e caminhos de servidor | `originalUrl` e caminho absoluto de comprovante expostos |
| M2 | **Média** | Servir estático de `uploads/` (risco futuro) | `file:uploads/` configurado como static-location |
| M3 | **Média** | Preço da foto extra inconsistente | Config global vs `pacote.precoFotoExtra` |
| M4 | **Média** | Carrinho sem validação de propriedade/estado da foto | Qualquer `fotoId` aceito no carrinho |
| L1 | **Baixa** | `X-Session-Id` controlado pelo cliente | Impersonação de carrinho |
| L2 | **Baixa** | Sem rate limiting | Enumeração / DoS / enchimento de disco |
| L3 | **Baixa** | Endpoints sem regra de segurança (funcionais) | `/avaliacoes` e `/sessoes` caem no `denyAll` (403) |

---

## Contexto do Fluxo Analisado

1. Cliente acessa a galeria via link público `/g/{token}` (token UUID com expiração de 15 dias).
2. Seleciona fotos do **pacote** (limite = `pacote.quantidadeFotos`).
3. Fotos além do pacote entram no **carrinho** como **fotos extras** (cobradas).
4. Checkout → cria `CompraExtra` → upload de comprovante → confirmação manual do admin → fotos marcadas `PAGA`.
5. Download permitido apenas para fotos `selecionadaPacote=true` ou `status=PAGA`.

Regra de download centralizada em `EcommerceService.isDownloadPermitido`:

```java
public boolean isDownloadPermitido(FotoEnsaio foto) {
    return foto.isSelecionadaPacote() || foto.getStatus() == StatusFoto.PAGA;
}
```

---

## Vulnerabilidades Detalhadas

### C1 — CRÍTICA: Download de originais sem pagar (IDOR / Controle de Acesso Quebrado)

**Local:**
- `photoizer-backend/.../foto/api/FotoController.java:58-66` — `servirOriginal`
- `photoizer-backend/.../auth/config/SecurityConfig.java:59` — `/api/v1/agendamentos/**` → `.authenticated()`
- `photoizer-backend/.../cliente/service/ClienteAuthService.java:58,72` — JWT com papel `CLIENTE`
- `photoizer-backend/.../auth/config/SecurityConfig.java:39` — `/api/v1/auth/cliente/registro` → `permitAll()`
- `photoizer-backend/.../foto/api/FotoEnsaioResponse.java:37` — expõe `originalUrl`

**Problema:**
- `GET /api/v1/agendamentos/{agendamentoId}/fotos/{fotoId}/original` serve o arquivo original em alta resolução exigindo apenas `authenticated()` — **qualquer papel**, inclusive `ROLE_CLIENTE`.
- Qualquer pessoa cria conta de cliente via endpoint público de registro e recebe um JWT válido (`ROLE_CLIENTE`).
- A resposta pública da galeria (`GET /api/v1/ecommerce/galeria/{token}`, `permitAll`) já entrega `originalUrl`, `id` e `agendamentoId` de cada foto.

**Exploração (3 passos):**
1. `GET /api/v1/ecommerce/galeria/{token}` → coleta todas as `originalUrl`/IDs (sem autenticação).
2. `POST /api/v1/auth/cliente/registro` → obtém Bearer token (público).
3. `GET {originalUrl}` com `Authorization: Bearer <token>` → **todas as fotos originais, de graça**, sem selecionar pacote e sem pagar extra.

**Impacto:** anula por completo a marca d'água, o limite do pacote e a cobrança de fotos extras. Exfiltração total do acervo de fotos.

---

### C2 — CRÍTICA: Seleção de fotos do pacote sem limite no servidor

**Local:**
- `photoizer-backend/.../ecommerce/service/EcommerceService.java:131-139` — `selecionarFotos`
- `photoizer-backend/.../ecommerce/service/EcommerceService.java:265-267` — `isDownloadPermitido`
- `photoizer-backend/.../ecommerce/api/EcommerceController.java:67-74` — endpoint público `/galeria/{token}/selecionar`
- `photoizer-backend/.../pacote/model/Pacote.java:36-39` — `quantidadeFotos`

**Problema:**
- O endpoint público `PATCH /galeria/{token}/selecionar` aceita uma lista arbitrária de `fotoIds` e marca todos como `selecionadaPacote=true`, **sem comparar com `pacote.quantidadeFotos`**.
- `isDownloadPermitido()` libera download de qualquer foto com `selecionadaPacote=true`.
- `downloadFoto` e `downloadZip` entregam todos os arquivos liberados.

**Exploração:**
```bash
curl -X PATCH /api/v1/ecommerce/galeria/{token}/selecionar \
  -H 'Content-Type: application/json' \
  -d '{"fotoIds":["<id1>","<id2>",...],"selecionada":true}'
curl /api/v1/ecommerce/galeria/{token}/download-zip   # todas as originais
```

O cliente também pode desmarcar e remarcar indefinidamente — não há contador nem estado persistente de limite.

**Impacto:** **vulnerabilidade central da regra de negócio** — o cliente burla a cobrança de fotos extras marcando-as como "do pacote".

---

### H1 — ALTA: Marca d'água frágil + preview em resolução total

**Local:**
- `photoizer-backend/.../foto/service/ImageProcessingService.java:29-59` — `aplicarMarcaDagua`
- `photoizer-backend/.../foto/service/FotoService.java:26-27` — `TEXTO_MARCA_DAGUA`, `OPACIDADE_MARCA=0.15`
- `photoizer-backend/.../ecommerce/api/EcommerceController.java:240-249` — `/api/v1/ecommerce/fotos/{fotoId}/watermarked` (público)

**Problema:**
- O arquivo "watermarked" é **resolução total** (mesmo width/height do original) com texto pequeno (24pt), opacidade baixa (15%) e grade com espaçamento de 80px.
- O endpoint é `permitAll()` e aceita qualquer `fotoId` (IDOR de preview).
- Cliente pode recortar as faixas sem marca d'água ou tratar a imagem, obtendo qualidade quase-original sem pagar.

**Impacto:** a marca d'água **não é um controle de acesso** eficaz; permite uso comercial do material sem pagamento.

---

### M1 — MÉDIA: Vazamento de URLs de originais e caminhos de servidor

**Local:**
- `photoizer-backend/.../foto/api/FotoEnsaioResponse.java:37` — `originalUrl` na resposta pública
- `photoizer-backend/.../foto/api/FotoEnsaioResponse.java:50` — `metadataExif` (privacidade)
- `photoizer-backend/.../ecommerce/service/EcommerceService.java:311-329` — `urlComprovante` com caminho absoluto
- `photoizer-frontend/.../ecommerce/components/MinhasComprasSection.tsx:172` — usa o caminho bruto como `href`

**Problema:**
- A galeria pública devolve a URL do arquivo **original** (habilita C1) e metadados EXIF.
- `AdminCompraDetalheResponse.urlComprovante` expõe **caminho absoluto do filesystem** do servidor via endpoints públicos (`/galeria/{token}/compras/{compraId}`).
- Frontend usa o caminho absoluto diretamente como `<a href>` (também é um bug funcional — aponta para o disco local do cliente).

**Impacto:** vazamento de informação sobre a topologia do servidor e enabler de C1.

---

### M2 — MÉDIA: Servir estático de `uploads/` (risco de defense-in-depth)

**Local:**
- `photoizer-backend/src/main/resources/application.properties` → `spring.web.resources.static-locations=file:uploads/`

**Problema:**
- Hoje o `.anyRequest().denyAll()` do Spring Security bloqueia `/uploads/**`, mas se alguém permitir estáticos (ou um proxy/nginx servir `/` diretamente), **todos os originais, comprovantes e thumbs viram públicos**.
- Os arquivos ficam em pastas previsíveis (`uploads/{agendamentoId}/orig_{uuid}.jpg`).

**Impacto:** alto risco futuro; mitigação preventiva recomendada (remover o mapeamento estático ou protegê-lo).

---

### M3 — MÉDIA: Preço da foto extra inconsistente

**Local:**
- `photoizer-backend/.../ecommerce/service/EcommerceService.java:120-122` — `getValorUnitarioFotoExtra()` (config `valorUnitarioFotoExtra`, default R$15,00)
- `photoizer-backend/.../ecommerce/service/EcommerceService.java:142-158,160-195` — cálculo e checkout usam o valor da config
- `photoizer-frontend/.../ecommerce/pages/CheckoutPage.tsx:47` — upsell usa `pacote.precoFotoExtra`

**Problema:** o checkout/calc da galeria usa a config global e **não** `pacote.precoFotoExtra`. O preço cobrado pode não corresponder ao pacote do agendamento.

**Impacto:** sub/sobre-cobrança; regra de preço inconsistente entre fluxos.

---

### M4 — MÉDIA: Carrinho sem validação de propriedade/estado da foto

**Local:**
- `photoizer-backend/.../ecommerce/service/EcommerceService.java:197-209` — `adicionarAoCarrinho`
- `photoizer-backend/.../ecommerce/service/EcommerceService.java:160-195` — `checkout`

**Problema:**
- `adicionarAoCarrinho` aceita qualquer `fotoId`, sem verificar se pertence ao agendamento ou se está publicada/visível.
- O `checkout` cobra por `itensCarrinho.size()` bruto (`:170`) e só depois filtra por agendamento ao vincular (`:184`) — possível mismatch de preço e inclusão de fotos ocultas/de outras galerias.

**Impacto:** cliente pode adicionar fotos de outras galerias ou ocultas; cobrança calculada sobre lista não filtrada.

---

### L1 — BAIXA: `X-Session-Id` controlado pelo cliente

**Local:**
- `photoizer-backend/.../ecommerce/api/EcommerceController.java:48-50` — `resolverSessionId`

**Problema:** o header `X-Session-Id` é gerado e enviado pelo cliente (`photoizer_cart_session` no `localStorage`). O servidor confia nele, permitindo assumir o carrinho de outra sessão.

**Impacto:** baixo (afeta apenas carrinho anônimo), mas não há vínculo com o servidor.

---

### L2 — BAIXA: Sem rate limiting

**Local:** endpoints públicos de `/api/v1/ecommerce/galeria/**`

**Problema:** checkout, seleção, favoritos e upload de comprovante não possuem limite de requisições.

**Impacto:** enumeração de IDs, DoS via geração de ZIP (`download-zip`) e enchimento de disco via `uploadComprovante` repetido.

---

### L3 — BAIXA: Endpoints sem regra de segurança (funcionais)

**Local:**
- `photoizer-backend/.../ecommerce/api/AvaliacaoController.java:16` — `/api/v1/avaliacoes`
- `photoizer-backend/.../ecommerce/api/SessaoController.java:16` — `/api/v1/sessoes`

**Problema:** não há regra no `SecurityConfig` para `/api/v1/avaliacoes/**` e `/api/v1/sessoes/**` → caem no `anyRequest().denyAll()` → retornam **403** para todos, inclusive para o próprio frontend da loja (depoimentos, sessões quebrados).

**Impacto:** funcional; também demonstra que novas rotas públicas precisam de revisão de segurança explícita.

---

## Notas Adicionais

- **Fotos ocultas no ZIP:** `getDownloadableFotos` (`EcommerceService.java:269-274`) não filtra `visivel`; fotos com `selecionadaPacote=true` e `visivel=false` entram no ZIP.
- **Resposta de `selecionarFotos`:** retorna também fotos de outros agendamentos (apenas as do token são alteradas, mas todas as IDs enviadas são devolvidas) — vazamento cruzado mínimo.
- **Duplicidade de seleção:** a mesma foto pode ser marcada como `selecionadaPacote` e depois comprada como extra (sem impedimento de estado).

---

## Plano de Correção por Fases

### Fase 1 — Crítico (bloquear a violação da regra de fotos extras)
1. **C2 — Limite de seleção no servidor:** em `selecionarFotos`, contar fotos já marcadas no agendamento; se `selecionada=true` e o total exceder `pacote.quantidadeFotos`, lançar erro (`IllegalArgumentException`/exceção de domínio). Validação dentro de transação para evitar corrida.
2. **C1 — Bloquear original via `agendamentos`:** em `FotoController.servirOriginal`/`servirThumb`, exigir papel de equipe (`hasAnyRole("ADMIN","FOTOGRAFO","EDITOR","AGENDADOR")`) — ou garantir que `ROLE_CLIENTE` não alcance o endpoint. Manter o download de cliente exclusivamente via `/galeria/{token}/download/{fotoId}` (que valida `isDownloadPermitido` + limite).

### Fase 2 — Alto / preparação de defesa
3. **H1 — Reforçar marca d'água:** aumentar opacidade/tamanho do texto, reduzir gaps da grade e gerar preview em resolução reduzida; impedir servir `watermarkedUrl` de fotos não publicadas.
4. **M1 — Sanear respostas públicas:** remover `originalUrl` e `metadataExif` do `FotoEnsaioResponse`; devolver apenas `watermarkedUrl`/`thumbUrl`. Corrigir `MinhasComprasSection` para usar o endpoint de comprovante em vez do caminho bruto.
5. **M2 — Proteger `uploads/`:** remover `spring.web.resources.static-locations=file:uploads/` (ou restringir) para eliminar risco de exposição direta.

### Fase 3 — Média (consistência da regra e validações)
6. **M3 — Preço por pacote:** usar `pacote.precoFotoExtra` no cálculo do carrinho e no checkout, com fallback para a config global.
7. **M4 — Validação no carrinho:** ao adicionar, conferir `agendamentoId`, `visivel=true` e `status=PUBLICADA`; alinhar contagem/preço com as fotos efetivamente válidas.
8. **Ajuste ZIP:** filtrar `visivel` e fotos de outros agendamentos em `getDownloadableFotos`/`downloadZip`.

### Fase 4 — Baixa (higiene de segurança)
9. **L1 — Sessão:** validar `X-Session-Id` (formato/pertinência) no servidor.
10. **L2 — Rate limiting:** adicionar limitação nos endpoints públicos da galeria (especialmente `download-zip`, `checkout`, `selecionar`, `comprovante`).
11. **L3 — Segurança explícita:** adicionar regras no `SecurityConfig` para `/api/v1/avaliacoes/**` (públicas: depoimentos) e `/api/v1/sessoes/**` (restrito a equipe).
12. **Testes:** criar testes de segurança (controller/serviço) cobrindo: limite do pacote, download sem pagamento, acesso ao original com `ROLE_CLIENTE`, e preço por pacote.

---

## Referências de Código

| Arquivo | Linhas |
|---------|--------|
| `EcommerceService.java` | 120-122, 131-139, 142-158, 160-195, 197-209, 265-274, 311-329 |
| `EcommerceController.java` | 48-50, 67-74, 240-249 |
| `FotoController.java` | 58-66 |
| `FotoEnsaioResponse.java` | 37, 50 |
| `FotoService.java` | 26-27, 84 |
| `ImageProcessingService.java` | 29-59 |
| `SecurityConfig.java` | 39, 59 |
| `ClienteAuthService.java` | 58, 72 |
| `Pacote.java` | 36-39 |
| `GaleriaClientePage.tsx` | 183 |
| `PhotoGrid.tsx` | 37 |
| `MinhasComprasSection.tsx` | 172 |
| `CheckoutPage.tsx` | 47 |
| `application.properties` | `spring.web.resources.static-locations` |
