# Contrato-modelo SaaS — Nível Setor Público (ARCABOUÇO — ED-05)

> **STATUS: PARCIAL / ARCABOUÇO — NÃO É MINUTA APROVADA, NÃO ASSINAR, NÃO USAR COM CLIENTE.**
> Este documento é a **estrutura** do contrato-modelo exigido pelo aceite da
> ED-05 ("contrato-modelo do nível Setor Público com as metas do ADR-0025").
> O **texto jurídico** das cláusulas pertence à **P3.9** ("Contrato SaaS, DPA,
> termos, SLA, suboperadores: minutas aprovadas"), **adiada pelo dono** para a
> onda dela. Onde a fonte do projeto não fixa valor, redação ou compromisso,
> este arcabouço usa o marcador **[A DEFINIR NA P3.9]** — nenhum número,
> prazo, multa ou percentual foi inventado aqui.
>
> Assinatura real: só na **LC.9**, com o piloto, antes do primeiro dado real
> (aceite da P3.9 e da LC.9 no PLANO-v3.0).

## 0. Divergência registrada na delegação (transparência)

A tarefa foi delegada com o título "Contrato-modelo SaaS e anexos" e deps
"P3.6 e P3.9". **Na fonte oficial do plano isso não confere:**

| Item | Delegação | Fonte (PLANO-v3.0 / tarefas-onda-A.json) |
|---|---|---|
| Título da ED-05 | "Contrato-modelo SaaS e anexos" | "Nível Setor Público: telefone, canal de contingência e plantão" (origem 18h-E7, E9; ADR-0025) |
| Deps da ED-05 | P3.6, P3.9 | **P2.15, P3.15, P3.16** |
| P3.6 | citada como fonte de SLA | P3.6 é "NFS-e de serviços avulsos" (ADR-0003) — **não** é fonte de SLA |
| Fonte do SLA | — | **P2.15** (política de SLA por contrato, core `V10_2`) + relatório mensal da **P3.15** |

Esta entrega segue a **fonte**: ED-05 real, com o contrato-modelo do nível
Setor Público como um dos seus cinco itens de aceite. A P3.9 entra como
dependência **do conteúdo jurídico** (é ela quem aprova as minutas das quais
este modelo deriva), não como dep formal da ED-05.

## 1. Aceite da ED-05 — o que este arcabouço cobre

Aceite (tarefas-onda-A.json, ED-05): *"telefone no Brasil e canal de
contingência publicados e testados com a ferramenta de chamados fora do ar;
escala de plantão para incidentes S1 montada antes da primeira produção e
acionada a partir dela (LC.13; ADR-0025, ajustado na v2.7); contrato-modelo
do nível Setor Público com as metas do ADR-0025; Item 3 do SAMAE
(manutenção e suporte) sem falha no ensaio; item 2.5 do SAMAE demonstrado
em conjunto com os alertas da ED-07"*.

| Item do aceite | Onde está nesta entrega | Estado |
|---|---|---|
| Contrato-modelo Setor Público c/ metas ADR-0025 | Este documento + Anexos I–IV | **Arcabouço pronto**; texto jurídico = P3.9 |
| Telefone no Brasil + canal de contingência | Anexo IV | Estrutura pronta; **número/canal reais não existem nas fontes — não inventados** |
| Escala de plantão S1 antes da 1ª produção (LC.13) | Anexo IV | Estrutura pronta; nomes/escala = **[A DEFINIR PELA OPERAÇÃO]** |
| Item 3 SAMAE (manutenção e suporte) sem falha no ensaio | Anexo I + roteiro de ensaio (§5) | Depende de **ensaio real** — não executado nesta sessão |
| Item 2.5 SAMAE c/ alertas da ED-07 | Anexo IV §4 | Depende da ED-07 e de ensaio — não executado |

## 2. Sumário do contrato-modelo (estrutura)

Partes e cláusulas **estruturais** — a redação de cada uma é da P3.9.
Entre colchetes, a fonte que fundamenta a existência do item (não a redação).

1. **Qualificação das partes** — contratante (tenant) e Ziip SAN.
   Campos de identificação: **[A DEFINIR NA P3.9]** (nenhum dado de empresa
   consta das fontes usadas; nada foi preenchido).
2. **Objeto** — licenciamento/uso do Ziip SAN como SaaS multi-tenant
   (ADR-0024; instância dedicada só para quem exigir em contrato, via LC.15).
3. **Nível de serviço contratado** — nível **Setor Público** (ADR-0025):
   acionado a partir do lançamento; o padrão do produto permanece inalterado
   para os demais níveis (decisões do plano, linha "Nível de serviço").
   Metas incorporadas por remissão ao **Anexo I**.
4. **Suporte e SLA** — chamado `SUP-AAAA-NNNNNN`, SLA **por contrato**,
   severidades **S1 a S5** (ADR-0020; decisões do plano, linha "Suporte e
   versão"). Política de SLA **referenciada no contrato** e registrada em
   `support.sla_policy` (core `V10_2`, P2.15). Detalhe: **Anexo I**.
5. **Disponibilidade** — meta de **99,9%** no nível Setor Público, medida a
   partir do lançamento, com relatório mensal por contrato (P3.15).
   Definição de janela de medição, exclusões e manutenção programada:
   **[A DEFINIR NA P3.9]** (as fontes fixam a meta e o relatório; não fixam
   as exclusões).
6. **Proteção de dados (LGPD)** — Ziip SAN **operador**, cliente
   **controlador** (aceite da P3.9; ADR-0002/0019). DPA como **Anexo II**.
7. **Cobrança do SaaS e suspensão** — cobrança por contrato, **sem bloqueio
   automático**; suspensão **sempre manual, com aviso prévio**, por
   `PLATFORM_OWNER` ou `PLATFORM_FINANCE` (este só por inadimplência
   contratual), com backoffice em somente leitura e portal, Pix, retornos e
   2ª via ativos durante a suspensão (ADR-0018/0019; decisões do plano).
   Prazos de aviso e carência financeira: **[A DEFINIR NA P3.9]**.
8. **Saída, exportação e eliminação** — **Anexo III** (P3.16/ADR-0025).
9. **Risco residual de desastre regional** — o contrato **declara** o risco
   residual: a arquitetura cobre falha de zona, ransomware e erro humano,
   **não** a perda da região inteira; nenhum backup/réplica fora de
   `sa-east-1` (LC.3; RNF-44). Redação da declaração: **[A DEFINIR NA P3.9]**.
10. **Contingência e plantão** — **Anexo IV**.
11. **Vigência, rescisão e penalidades gerais** — **[A DEFINIR NA P3.9]**
    (nenhum prazo de vigência, multa rescisória ou percentual geral consta
    das fontes; o único percentual estrutural do projeto é o de multa por
    chamado estourado **dentro da política de SLA do contrato** — Anexo I).
12. **Foro e disposições gerais** — **[A DEFINIR NA P3.9]**.

## 3. Pontos de inserção (onde a P3.9 entra)

Cada **[A DEFINIR NA P3.9]** deste pacote é um ponto de inserção
relacionado no `INDICE-E-RASTREABILIDADE.md` (que lista os arquivos do
pacote e a fonte de cada afirmação normativa, sem numeração individual
dos pontos). Quando a P3.9 for executada e as minutas
forem **aprovadas pelo piloto**, a integração é: (a) substituir cada
marcador pela redação aprovada; (b) fixar no contrato o **teto** de
`lgpd.operator_notice_hours` (ver Anexo II); (c) preencher a política de SLA
do contrato (`support.sla_policy`) com prazos/metas/percentuais aprovados
(Anexo I); (d) remover este cabeçalho de arcabouço.

## 4. O que NÃO está neste documento de propósito

- Nenhuma cláusula jurídica redigida (competência da P3.9 + aprovação do piloto).
- Nenhum telefone, e-mail, endereço, CNPJ, nome de plantonista ou canal
  concreto: **não constam das fontes** e não foram inventados.
- Nenhum compromisso além dos que as tarefas citadas já fixam.
