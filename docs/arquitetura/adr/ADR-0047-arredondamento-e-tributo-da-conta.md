# ADR-0047 — Regra única de arredondamento e de tributo na conta

- **Status:** Aceito, 03/11/2026 (frente FA-billing, onda P1; tarefa P1.16, plano v3.0 linha 642)
- **Afeta:** ADR-0003 (motor fiscal portado); `commercial/ziipsan-billing` (`Money`,
  `TaxWithholding`, `BillCalculator`); migration `V4_6`; vetores
  `commercial/ziipsan-billing/src/test/resources/bill-rounding-vectors.txt`
- **Não afeta:** o documento fiscal (NFS-e/NFAg-e), que tem regra própria (seção 4).

## Contexto

A conta tinha duas fontes de verdade sobre tributo: o código (`BillCalculator`:
total = água + esgoto + débitos − créditos − impostos retidos) e o banco (`V4_1`:
o mesmo CHECK, sem dizer o que `taxes` significa). O relatório FISCAL-PORTE-JAVA
(seção 4) avisou que o destaque de IBS/CBS obrigaria a revisar esse CHECK antes do R4,
e que o C# usa half-even para NFS-e (NT 007/2026) enquanto o Java usa HALF_DOWN para
a retenção, herdado do legado sem norma (relatório 08, E-20). Sem uma decisão escrita,
uma frente futura poderia enfiar IBS/CBS em `taxes_value` e continuar passando no
CHECK antigo — com o total errado em silêncio.

## Decisão

### 1. A conta tem uma única regra de arredondamento por operação (nada de "modo padrão")

| Operação | Modo | Onde |
|---|---|---|
| Valor de linha de faixa e de parcela fixa, desconto social, esgoto, rateio por dias, multa e juros | HALF_UP, 2 casas | `Money`, `TariffEngine`, `LateFeeCalculator` |
| Parcela de débito/crédito | DOWN, 2 casas; centavos na última | `Installments` |
| Base do imposto retido | DOWN, 2 casas | `TaxWithholding` |
| Imposto retido (cada um) e total retido | HALF_DOWN, 2 casas | `TaxWithholding` |
| Resíduo de arredondamento da retenção | o último absorve; resíduo negativo volta aos anteriores, nunca imposto negativo | `TaxWithholding` |

Nenhum `BigDecimal` nasce de `double` (regra já guardada por `SourceHygieneTest`).

### 2. O tributo da conta, em P1, é só retenção — e o banco trava isso

- `bills.taxes_value` = soma dos `bill_taxes` produzidos por `TaxWithholding`.
- `bills.tax_treatment` (V4_6) só admite `WITHHOLDING_ONLY` (CHECK
  `chk_bills_tax_treatment`), e o CHECK nomeado `chk_bills_total_with_withholding_tax`
  reafirma a fórmula do total com o termo de tributo assim definido. O CHECK da V4_1
  é mantido (migrations são imutáveis); o nomeado é a versão auditável da P1.16.
- IBS/CBS **não entram** no total da conta em P1. Em 2026 eles são só destaque no
  documento fiscal; se ficarem dentro ou fora do preço, a P2.6 decide — com o CHECK
  desta ADR mudado por migration própria (regra NT 2026.004: a partir de 2027 o total
  do documento é líquido + IBS + CBS). Nenhuma frente pode mudar o regime só mudando
  código: o CHECK recusa qualquer outro `tax_treatment`.

### 3. Vetores C# × Java: o arquivo é o contrato, cada lado roda o seu

`bill-rounding-vectors.txt` (formato `id|operação|campos`, listas por vírgula,
decimais em texto) é lido pelo JUnit (`BillRoundingVectorsTest`, 13 casos: R-01–R-03,
W-01–W-04, B-01–B-04, I-01–I-02) e pelo xUnit do Ziip Cloud quando ele mudar.
Divergência bloqueia o merge (FISCAL-PORTE-JAVA §5). O lado Java passa nesta frente;
a execução do lado C# é externa a este repositório e fica como pendência registrada
no relatório da frente — não é afirmada aqui.

### 4. O que NÃO é esta regra (fronteira com o fiscal)

O documento fiscal usa half-even para PIS/Cofins (NT 007/2026, tolerância de R$ 0,01
só para `vPis`/`vCofins`, não para IBS/CBS) e as regras da NT 004 para IBS/CBS. Isso
pertence aos módulos fiscais (P1.8 `V4_5`, P2.6 `V4_7`), não à conta. Confundir os
dois — aplicar half-even à retenção da conta, ou HALF_DOWN ao documento — é erro de
regime, não de arredondamento.

### 5. Partição e auditoria por lote: o que a V4_6 faz em P1 e o que fica para P2.3

- P1 (esta frente): `bill_audit_batches` + `bills.audit_batch_id` (opcional). Um lote
  é uma execução (`run_id`) por mês de referência, com quantidade de contas e total.
- P2.3 (não antecipado aqui): particionamento declarativo de `bills`/`bill_items` por
  `reference_month` e auditoria por lote obrigatória por execução, com prova de poda
  por partição. Particionar `bills` exige PK composta `(tenant_id, reference_month, id)`
  e `reference_month` em toda FK filha (`bill_items`, `bill_debits`, `bill_credits`,
  `bill_taxes`) mais chave composta no JPA — mudança de escala P2, que colidiria com
  a P1.8 (`fiscal_document_id`, V4_7) e a P2.5 (colunas novas em `bills`) se feita
  agora. Partição por hash de `id`, ou uma segunda tabela particionada cópia, foi
  rejeitada: não poda nenhuma consulta por mês e duplicaria a fonte de verdade.

## Pendências (não decididas por esta ADR, não inventadas aqui)

1. Confirmação no DOU do piso da tarifa social (50% até 15 m³) antes da ativação em
   produção — ver `SocialBenefitFloor` (P1.5): se o DOU divergir, mudam as constantes,
   não a forma da validação.
2. Trava de coerência entre `social.base_import` / `social.list_source` e
   `reg.legal_nature` (cessão direta de CadÚnico só para ente de direito público,
   Decreto 11.016/2022): regra de validação de parâmetros da plataforma, fora do
   escopo de banco desta frente; registrada no relatório da P1.5 para a integração.
3. Regime IBS/CBS na conta (dentro/fora do preço) — decisão da P2.6, com migration
   própria alterando `chk_bills_tax_treatment`.
