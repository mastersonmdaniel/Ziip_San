# ED-23 — Protocolo da revisão por vazamento: fiação na integração

A ED-23 entrega o núcleo puro do protocolo (fases, vistoria, laudo,
fluxo de decisão) no módulo `commercial/ziipsan-billing`, pacote
`br.com.ziipsan.commercial.billing.leakprotocol`. Persistência,
controllers e adaptadores das portas são da integração, como nas
ED-20/ED-36/ED-41.

## Fiação das portas (`LeakProtocolPorts`)

| Porta | Adaptador real | Observação |
|---|---|---|
| `SigningPort` | `SignatureService.sign` da P3.34 | Laudo = `LAB_REPORT`; decisão = `SERVICE_ORDER_ACCEPTANCE`. O nível é o `sign.min_level.<tipo>` do tenant, exigido dentro da P3.34; sem nível vigente, ela recusa e o protocolo não avança. O recibo guarda código verificador + nível aplicado. |
| `RectificationPort` | `BillMaintenanceService.rectify` da P2.20 | Construir o `RectifiedValues` a partir dos volumes da decisão da P3.28 (`LeakDecision.revisedWater/revisedSewer`); a chave de idempotência é a do protocolo. Limites contra o usuário, fila de análise e dupla aprovação são aplicados pela P2.20. **Nenhum outro caminho de crédito/retificação pode ser criado.** |
| `NotificationPort` | P3.5 / ED-41 | Disparar o modelo do tenant para a fase (`REPORT_SIGNED`, `CLOSED_GRANTED`, `CLOSED_DENIED`); modelo/canal são das donas. |
| `CutoffSuspensionPort` | `ReviewSuspensionRule` da P2.20 | Só consulta, para exibir no protocolo se a conta está suspensa de corte enquanto em revisão (regras da P3.4). |

## Ordem de composição de uma revisão completa

1. P3.28 abre o pedido (`leak_reviews` + OS `LEAK_REVIEW` filha da
   `BILL_REVIEW` da P2.20), já com prazo/elegibilidade conferidos.
2. ED-23 abre o protocolo sobre esse pedido (`leakReviewId` +
   `serviceOrderId` obrigatórios — protocolo sem pedido da P3.28 não
   existe).
3. Campo (ED-20): `beginInspection` → execução da OS no app →
   `recordInspection` com a `FieldInspection` montada dos dados do
   comprovante da ED-20 (fotos CLEARED, identidade do hidrômetro).
4. Laudo: `LeakReport` emitido → `LeakDecisionFlow.signReport`
   (P3.34) → fase DECISION. Laudo `INCONCLUSIVE` assinado não abre
   decisão: `returnToInspection` com motivo.
5. Decisão: GRANT exige laudo `LEAK_CONFIRMED` e os volumes da
   decisão da P3.28; `decideGranted` assina a decisão (P3.34), pede a
   retificação à P2.20 e só fecha com o recibo dela. DENY assina e
   fecha sem retificação.
6. Cada transição grava `PhaseEvent` (trilha) e notifica a fase.

## Persistência

`db/pendente/ED-23__leak_review_protocol.sql` (sem número Flyway —
regra 4 da cascata): `leak_review_protocols` (FK para `leak_reviews`
da V4_17 da P3.28), `leak_review_phase_events`,
`leak_review_inspections`, `leak_review_reports`. As assinaturas
vivem em `document_signatures` (V1_14 da P3.34); as tabelas da ED-23
guardam só código/nível fotografados.

## O que NÃO existe por desenho

- Nenhum cálculo de desconto (P3.28), nenhuma retificação própria
  (P2.20), nenhum nível de assinatura presumido (P3.34), nenhum prazo
  de fase ([A DEFINIR], pendência P-ED23-03), nenhum envio de mensagem
  próprio (P3.5/ED-41).
