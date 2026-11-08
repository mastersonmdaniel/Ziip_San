# Anexo I — SLA de suporte (Nível Setor Público)

> Fontes: **P2.15** (política de SLA por contrato, core `V10_2`,
> `support.sla_policy`), **P3.15** (relatório mensal por contrato na Central),
> ADR-0020 e ADR-0025. Percentuais e prazos **da política de cada contrato**
> não são fixados pelo plano — são preenchidos por contrato a partir da
> minuta da P3.9. Este anexo fixa a **estrutura e as regras de cálculo** que
> o projeto já entregou; não fixa valores novos.

## 1. Estrutura da política (P2.15)

- Política de SLA de suporte **referenciada no contrato** e registrada por
  contrato (não é global do produto).
- **Cinco severidades, S1 a S5**, definidas por **impacto × criticidade**
  (matriz impacto × criticidade no mapa de migrations do plano, linha da
  `V10_2`).
- Para **cada** severidade, a política registra:
  1. prazo de **primeira resposta**;
  2. prazo de **solução**;
  3. **meta de aderência** (%);
  4. **multa do contrato** (% — ver §3).
- Prazo calculado com **feriado municipal** e **pausa** do chamado (casos de
  cálculo fazem parte do aceite da P2.15).
- **Alerta a 80% do prazo** e **alerta no estouro** (P2.15).

## 2. Valores de referência que o PROJETO já testa (não são a política)

O aceite da P2.15 manda **testar** a política com dois conjuntos reais de
edital — eles são **massa de teste da regra**, não valores deste modelo:

| Conjunto | Prazos testados (massa de teste da P2.15) |
|---|---|
| Videira | 2, 4, 6, 16 e 32 h úteis |
| SAMAE | 2, 4, 16, 72 e 160 h |

Os prazos **deste** contrato-modelo, por severidade (primeira resposta e
solução), são: **[A DEFINIR NA P3.9]** — a minuta aprovada é quem os fixa
na política `support.sla_policy` de cada contrato.

Metas de aderência por severidade (S1–S5): **[A DEFINIR NA P3.9]**.
Percentuais de multa por severidade (S1–S5): **[A DEFINIR NA P3.9]**.

## 3. Regra de cálculo da multa (entregue na P3.15, semântica da P2.15)

Fonte: `MonthlyContractReport` (entrega local da P3.15) — código, não
interpretação nova:

- A multa é **soma por chamado estourado**: cada resolução fora do prazo
  soma `mensalidade × penalty_pct / 100` (centavos, arredondamento HALF_UP
  por chamado). **Não** é "1× por severidade abaixo da meta": a comparação
  com a meta de aderência é informativa; quem gera multa é o **estouro**.
- **Nenhum teto de multa é aplicado** no cálculo, porque **nenhum teto
  consta das fontes**. Se a minuta da P3.9 fixar teto, ele entra aqui:
  teto de multa: **[A DEFINIR NA P3.9]**.
- O percentual usado no exemplo do código da P3.15 é ilustrativo de teste;
  **não** é percentual deste contrato.

## 4. Disponibilidade (ADR-0025 / P3.15)

- Meta de disponibilidade do nível Setor Público: **99,9%**, medida **a
  partir do lançamento**.
- **Relatório mensal por contrato** na Central Ziip: disponibilidade e
  **aderência por severidade**, com o **cálculo da multa** (P3.15).
- No nível padrão (STANDARD) **não** há meta de 99,9% no aceite: a
  disponibilidade é calculada e exibida, sem veredito de meta (P3.15).
- Janela de medição, manutenções programadas e exclusões do cálculo:
  **[A DEFINIR NA P3.9]**.
- **Premissa provisória vigente (registro — decisão expressa pendente da
  P3.9):** a entrega da P3.15 (`MonthlyContractReport`) já adotou, no
  código, a premissa de que o **denominador da disponibilidade exclui a
  manutenção planejada** (`minutosMedidos = diasMedidos × 1440 −
  manutençãoPlanejada`; nota `availabilityDenominatorNote` do relatório).
  Essa premissa está registrada no código e no relatório da P3.15 como
  **provisória, pendente de conferência contra o contrato-modelo
  (P3.9/ED-05)**: cabe à P3.9 **confirmá-la** ou, se decidir o contrário,
  **mandar corrigir o código entregue da P3.15** — não decidir em
  silêncio divergindo dele.

## 5. Medição e evidência

Relatório mensal de **FRT, MTTR, SLA e CSAT** na Central (aceite da P2.15).
Chamados identificados como `SUP-AAAA-NNNNNN` (ADR-0020). Retenção dos
chamados: **vigência do contrato + 5 anos**, com minimização (ADR-0020;
decisões do plano) — a exceção dessa retenção no certificado de descarte é
tratada no Anexo II/III.

## 6. Relação com o Item 3 do SAMAE

O Item 3 do SAMAE (manutenção e suporte) precisa sair **sem falha no
ensaio** (aceite da ED-05). Este anexo é a base documental desse ensaio; o
ensaio em si **não foi executado** nesta entrega (ver relatório da ED-05).
