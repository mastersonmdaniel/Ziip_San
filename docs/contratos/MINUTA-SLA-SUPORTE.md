# MINUTA — Política de SLA de Suporte (P3.9)

> **MINUTA — NÃO ASSINAR. Aprovação do piloto pendente (piloto ainda não escolhido — P0.30). Assinatura só na LC.9, antes do primeiro dado real.**
>
> Estado desta minuta: **PRODUZIDA COM APROVAÇÃO PENDENTE** (P3.9). Anexo do
> Contrato SaaS. Fontes: ADR-0019 (item 1: política de SLA referenciada no
> contrato), ADR-0020 (chamados, severidades S1–S5 do ADR-0025, referência
> S1), P2.15 (política por contrato em `support.sla_policy`, core `V10_2`),
> P3.15 (regra de cálculo da multa e relatório mensal) e Anexo I da ED-05.
> **Nenhum prazo, meta ou percentual de política foi inventado:** os valores
> da política de cada contrato são **[A DEFINIR]** até a aprovação.

## 1. Estrutura da política (fixada pelas fontes)

1. A política de SLA de suporte é **por contrato** — referenciada no Contrato
   SaaS e registrada em `support.sla_policy` (P2.15; ADR-0019, item 1) — com
   o **calendário** do contrato.
2. Todo chamado tem protocolo **`SUP-AAAA-NNNNNN`** (ADR-0020, item 3) e é
   classificado em uma de **cinco severidades, S1 a S5**, definidas por
   **impacto × criticidade** (ADR-0020; ADR-0025; P2.15).
3. Para **cada** severidade, a política registra: (1) prazo de **primeira
   resposta**; (2) prazo de **solução**; (3) **meta de aderência** (%);
   (4) **percentual de multa** do contrato (`penalty_pct`) — P2.15.
4. O prazo é calculado com **feriado municipal** e com **pausa** do chamado
   (P2.15). Há **alerta a 80% do prazo** e **alerta no estouro** (P2.15).
5. No **nível padrão**, os prazos de cada severidade seguem a política do
   contrato, **sem multa**; no **nível Setor Público**, há prazo de solução,
   aderência e multa (ADR-0025, via ajustes do ADR-0020).
6. Retenção dos chamados: **vigência do contrato mais 5 anos** (ADR-0020) —
   exceção registrada no certificado de descarte conforme o DPA.

## 2. Tabela da política — valores [A DEFINIR]

| Severidade | 1ª resposta | Solução | Meta de aderência | Multa (`penalty_pct`) |
|---|---|---|---|---|
| S1 | [A DEFINIR] | [A DEFINIR] | [A DEFINIR] | [A DEFINIR] |
| S2 | [A DEFINIR] | [A DEFINIR] | [A DEFINIR] | [A DEFINIR] |
| S3 | [A DEFINIR] | [A DEFINIR] | [A DEFINIR] | [A DEFINIR] |
| S4 | [A DEFINIR] | [A DEFINIR] | [A DEFINIR] | [A DEFINIR] |
| S5 | [A DEFINIR] | [A DEFINIR] | [A DEFINIR] | [A DEFINIR] |

**Referência — não é valor fixado:** o ADR-0025 registra, para a severidade
**S1, prazo de solução em 2 horas úteis COMO REFERÊNCIA** (citado nos ajustes
do ADR-0020). Esta minuta cita a referência como referência; o valor que
valerá na política de cada contrato é o da tabela acima, quando definido e
aprovado.

**Massa de teste — não é política:** os conjuntos **Videira** (2, 4, 6, 16 e
32 h úteis) e **SAMAE** (2, 4, 16, 72 e 160 h) são os casos que o aceite da
P2.15 manda **testar**; são massa de teste da regra de cálculo, **não**
valores contratuais desta minuta (Anexo I da ED-05, §2).

## 3. Regra de cálculo da multa (redigida — P3.15)

1. A multa é **soma por chamado estourado**: cada resolução fora do prazo
   soma `mensalidade × penalty_pct / 100`, em centavos, com arredondamento
   **HALF_UP por chamado** (`MonthlyContractReport`, entrega da P3.15;
   semântica da P2.15).
2. **Não** é "1× por severidade abaixo da meta": a comparação com a meta de
   aderência é **informativa**; quem gera multa é o **estouro** do prazo.
3. **Teto de multa:** [A DEFINIR — nenhum teto consta das fontes e nenhum é
   aplicado no cálculo entregue da P3.15; se a contratação fixar um teto,
   ele entra nesta cláusula e no cálculo].
4. O percentual usado no exemplo do código da P3.15 é ilustrativo de teste e
   **não** é percentual desta política.

## 4. Disponibilidade e relatório mensal (P3.15)

1. **Nível Setor Público:** meta de disponibilidade de **99,9%**, medida **a
   partir do lançamento** (ADR-0025, ajustado pelo ADR-0033).
2. **Nível padrão:** **não há meta** de disponibilidade nas fontes; a
   disponibilidade é calculada e exibida, sem veredito de meta (P3.15).
3. **Relatório mensal por contrato** na Central Ziip: disponibilidade e
   **aderência por severidade**, com o **cálculo da multa** (P3.15). O
   relatório de suporte inclui **FRT, MTTR, SLA e CSAT** (P2.15).
4. Janela de medição da disponibilidade: [A DEFINIR — as fontes fixam a meta
   e o relatório, não a janela].
5. Exclusões do cálculo e tratamento da manutenção programada:
   [A DEFINIR — ver o registro de decisão pendente no §5].

## 5. DECISÃO EXPRESSA PENDENTE — premissa provisória da P3.15 (registro)

A entrega da P3.15 (`MonthlyContractReport`) adotou no código, como
**premissa provisória**, que o **denominador da disponibilidade exclui a
manutenção planejada** (`minutosMedidos = diasMedidos × 1440 −
manutençãoPlanejada`). Esta minuta **registra a premissa como DECISÃO
EXPRESSA PENDENTE**: ela **não é confirmada nem rejeitada** aqui, porque
nenhuma fonte a fixa como regra contratual, e esta minuta **não diverge dela
em silêncio**. Na aprovação (piloto/dono), a decisão deve ser tomada
expressamente: **confirmar** a premissa nesta cláusula **ou** determinar a
**correção do código entregue da P3.15**. Enquanto a decisão não é tomada, o
cálculo vigente é o do código da P3.15, com esta ressalva registrada
(também registrada no Anexo I, §4, da ED-05).

## 6. Plantão

1. **S1:** escala de plantão montada **antes da primeira produção** e
   acionada a partir dela (LC.13; Anexo IV da ED-05). Nomes, janelas e canal
   de acionamento são dados da operação: [A DEFINIR PELA OPERAÇÃO — fora do
   escopo desta minuta].
2. **S2–S5:** plantão **não consta das fontes**. Esta minuta **não o
   promete**. Criá-lo é decisão expressa futura: [A DEFINIR — decisão do
   dono/piloto; sem decisão, vale somente o plantão S1 do §1º].
