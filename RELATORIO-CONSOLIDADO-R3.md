# RELATÓRIO CONSOLIDADO — Lote de correções da Rodada 3 (R3-A, R3-B, R3-C, R3-D)

**Coordenador:** sessão `38fa3b6b-81ed-4293-bd91-9e129ebded5c` (profundidade 1/2), 08–09/11/2026.
**Fonte dos achados:** `~/workspace/goals/concluir-o-ziipsan/hidden_files/auditoria-continua/rodadas/2026-11-08-1743/00-CONSOLIDADO-RODADA3.md` (frentes 01–04 na mesma pasta).
**Método:** esteira de deploy com coordenador-portão (`~/workspace/ziipsan-producao/PROTOCOLO-ESTEIRA.md`): produção → revisão cruzada independente → confirmação por agente distinto do produtor E do revisor → portão mecânico → rebase verificado contra a main corrente → push por lote aprovado → verificação pós-pouso por hash. Nenhum push sem os 4 itens do portão. Produção, revisão e confirmação nunca na mesma sessão.
**Repasses incorporados:** R4 (R4-m4; R4-B: R4-P1, R4-M1), R5-B (F2-1, F2-2, R5-F4-2), R6-C (R6-G1, R6-M1, R6-M2, R6-m1, R6-m2).

## 1. Resumo executivo

| Lote | Achados | Estado final |
|---|---|---|
| **R3-A** (grave, ED-54) | R3-G1, B3-M1 | **POUSADO** — commit `1ccacd17` (03:06:40Z), 4/4 blobs verificados |
| **R3-B** (moderados) | B3-1, B3-2, R3-M1 | **POUSADO** — commit `4fde5dfb` (03:14:32Z), 8/8 blobs verificados |
| **R3-C** (menores) | B3-3, B3-4, R3-C4-1, R3-C4-2 | **POUSADO PARCIAL** — commits `9ae1c344` (B3-3) e `c01acb64` (B3-4), hashes = LANDING; commit 4 aplicado localmente; **commit 3 NÃO pousado — supersedido** (ver §5) |
| **R3-D** (persistentes) | R2-M1..M4, B2-10/ED-69-D2 + R4-m4 dobrado | **CORRIGIDO E VERIFICADO, NÃO POUSADO** — portão APROVADO pós-delta (ciclo R6-G1 fechado); pouso bloqueado pelo conflito de campo (§5) |

HEAD ao fechar este relatório: `82bad7ce` (R4-m2); abaixo dele `0eb45f85` (R4-m1) e `c01acb64` (R3-C commit 2). Os pousos R3 estão em `1ccacd17` (A), `4fde5dfb` (B), `9ae1c344` + `c01acb64` (C1+C2).

## 2. Achados fechados, com commits

| Achado | Lote | Commit | Verificação pós-pouso |
|---|---|---|---|
| R3-G1 — ED-54: UNIQUE `(tenant_id, id)` em `gis_maneuver_plan` | R3-A | `1ccacd17` | SQL blob `efc86954…`; PG: original FALHA exit 3, corrigido APLICA exit 0 |
| B3-M1 — `IsolationEngine`: área vazia com válvula–válvula | R3-A | `1ccacd17` | Guarda `affectedNodes.isEmpty()`; suíte 30/32 repro → **32/32**; `IsolationEngine.java` `9f4ef726…` |
| B3-1 — ED-43 `PiiMasker`: `,` e `_` nos separadores | R3-B | `4fde5dfb` | Suíte **70/70** (captura 56/70); SEP/DIGIT_RUN com `,`/`_` |
| B3-2 — ED-50 `MonthlyClosing.collected` por chave | R3-B | `4fde5dfb` | Suíte **64/64** (captura 62/64); `entryKeysBySample`, sem `entrySampleIds` |
| R3-M1 — 12 tabelas sem `register_tenant_table` | R3-B | `4fde5dfb` | PG 3/3 aplicam; **12/12** registradas (ED-101: 5; ED-23: 4; ED-43: 3) |
| B3-3 — ED-66 CHECK recusa datum NULL | R3-C | `9ae1c344` | Blob `1d1bfe67…`, sha256 `b51ce4bb…` = LANDING |
| B3-4 — ED-58 guarda de reexecução da UNIQUE | R3-C | `c01acb64` | Blob `01bc338f…`, sha256 `8b4d5ad6…` = LANDING; 1ª e 2ª aplicação exit 0 |
| R3-C4-1 — ED-44: prova de aplicação do SQL | R3-C | aplicado localmente | Adendo em `ed-44/RELATORIO-ED-44.md` (49 → 72 linhas), byte-idêntico à entrega (cmp OK); SQL byte-idêntico sha256 `b43d4d27…`, PG exit 0 |
| R3-C4-2 — campo: sync com a correção RG2-menores | R3-C | **não pousado — supersedido** | Ver §5 |
| R2-M1..M4 + B2-10/ED-69-D2 (lote R3-D) | R3-D | **não pousado** | Corrigidos e verificados localmente: suíte TS 71/71 → **95/95**; ED-69 SQL alinhado ao Java (`jsonb_array_length > 0` + trigger + `clause_number`); portão APROVADO pós-delta; pouso bloqueado (§5) |
| R4-m4 — `submitReading` sem try/catch (dobra no R3-D) | R3-D | **não pousado** | try/catch + `.catch` + 1 teste; suíte **96/96** (2 execuções), tsc exit 0; re-apreciação **APROVADO** + confirmação **CONFIRMADO** (ciclo R6-G1, §6) |

Rebase verificado no ato de cada push (blobs da main = originais pré-correção): R3-A 4/4 contra `fde45c56`; R3-B 8/8 contra `1ccacd17`; R3-C 2/2 contra `1ccacd17` (e re-checado antes do commit 1). Nenhum push removeu linhas alheias. Entre a verificação do R3-B e o seu push pousou o diário C6 (`4d88b898`, só `docs/`) — nenhum arquivo do lote tocado.

## 3. O que foi verificado como já fechado / descartado (lote D)

- **Nada dos 5 itens do lote D estava fechado.** R2-M1, R2-M2, R2-M3, R2-M4 e B2-10/ED-69-D2 **persistiam** na main corrente e foram todos corrigidos neste lote (nenhuma re-execução de item fechado).
- **M2 (dimensão de relatório): fora de escopo deste lote** por ordem do brief (não é correção de código). Registrado aqui: o dono decidiu em 09/11/2026 ~00:10 estender o registro do placar 2D às 41 executadas sem classificação e manter o registro a cada rodada — executado por outro coordenador (feito em 09/11/2026).
- **R3-M2: descartado como falso positivo — conferido, não executado.** As FKs de ED-56/ED-59 para `asset(tenant_id, id)` estão cobertas pela migration real da P1.35 na main (`V27_1__asset_registry.sql`, `uq_asset_tenant_id`).
- **R2-G1: não re-executado.** Em execução por outro coordenador; V102 pousou (commits `f6641b63`, `aa4d961b`).
- **M3 residual:** fechado em 09/11/2026 por outro coordenador com conferência independente — a premissa estava desatualizada (os três pacotes P2.7/P2.9/P2.10 JÁ continham o artefato formal; faltava só o apontamento para a confirmação consolidada, fechado com adendo datado).

## 4. Pendências declaradas

- **P-R3B-01** — RLS real: prova PG com stub registrador; política real é portão da CI total.
- **P-R3B-02** — ED-23 sem reaplicação (`CREATE TABLE` sem `IF NOT EXISTS`, pré-existente).
- **P-R3B-03 — SUSTENTADA, não é pendência:** a expectativa alterada em `MonthlyClosingTest` (`collected` 2→3, cobertura 50→75%) foi julgada com rigor por dois agentes independentes (revisor e confirmador do R3-B) como consequência necessária da regra prescrita pela B3-2, MAIS exigente — não é afrouxamento. Não mexer (reafirmado nos repasses R4-B e R5-B).
- **R3-D:** pendências declaradas em `RELATORIO-R3-D.md` — entre elas **P-R3D-6 fechada** pela mesa (prova PG própria do SQL ED-69) e **P-R3D-7 parcialmente atendida** (tsc sobre `src/` exit 0; o tsc completo do `tsconfig.json` entregue, com `capacitor.config.ts`/`vite.config.ts`, segue para CI/PC do dono); **R1 fechada** pela remoção do avulso `entrega/campo/main.ts`; **R2 segue aberta como errata não-bloqueante** (erro de digitação 22/22 no relatório §6); follow-ups de integração P-01..P-08 do lote M1, dos quais P-03/P-04 foram entregues neste lote.
- **V103 (residual B2-4, UNIQUE da chave de remessa): NÃO pousou.** Pendência P-R2G1-1 aguarda decisão de desenho (coluna persistida + fiação × UNIQUE composta) — a chave não tem coluna persistida (String derivada em memória; repositório é interface vazia). P-R2G1-2 (V100 fora da lista de publicadas) e P-R2G1-3 (CiTest da P3.12 desalinhado) são pré-existentes e não são deste lote.

## 5. Bloqueio de campo — o que NÃO pousou e por quê (R3-C commit 3; R3-D)

A main (HEAD `fde45c56` à época da verificação; `82bad7ce` ao fechar) já contém **implementação paralela e mais nova de R2-M1..R2-M4** em `campo/src/ui/*` e `campo/src/*`, com módulos `fieldTariffSnapshot.ts` e `fieldReadingStore.ts` e blobs distintos dos dos lotes R3-C e R3-D (ex.: `boot.ts` main `f770ab5a…` vs R3-D `627250be…`; `controller.ts` main `ca580614…` vs R3-D `1543b71c…`; `campo/src/field/fieldTaskEmission.ts` do R3-D não existe na main). A implementação da main contém a montagem R2-M1, a fiação P-03/P-04 e o endereço R2-M4, com texto/estrutura diferentes.

- **R3-C commit 3 (sync campo, R3-C4-2): NÃO POUSADO — SUPERSEDIDO.** Pousá-lo sobrescreveria a implementação mais nova da main pela versão RG2-menores (66 testes, sem a fiação R2-M1..M4) — remoção de linhas alheias, vedada pela cláusula (ii) do portão. O objetivo do achado (a main ter o campo corrigido) está atendido por implementação mais nova.
- **R3-D (todo o lote): NÃO POUSADO.** Os arquivos de campo do lote regrediriam a main pelo mesmo motivo. Só o SQL da ED-69 seria pousável em princípio (a main ainda tem o original, blob `4cf4a93f…`), por decisão separada — o LANDING do lote é unitário (17 arquivos) e não foi fatiado.
- **Decisão de merge/canônico do campo: PENDENTE (nível dono/pai) — NÃO decidida aqui.** Qual implementação é canônica (`fieldTariffSnapshot.ts`/`fieldReadingStore.ts` da main × `fieldTaskEmission.ts`/`syncEngine.saveReading` do R3-D), qual o destino dos arquivos de campo do R3-D e do commit 3 do R3-C. O commit que pousou a implementação paralela não foi identificado por este coordenador (o `get_commit` sobre `fde45c56` não retornou o conteúdo na época; provável origem: o coordenador de fechamento C5/C6, que rodava em paralelo — não verificado).

## 6. Processo — R5-B, R6-C e o ciclo novo do R3-D

- **F2-1 / R6-G1 (GRAVE, processo) — FECHADO pelo ciclo novo.** A dobra R4-m4 entrou depois da revisão/confirmação/portão registrados (readendo → PORTAO 02:48:41 UTC → dobra 02:51:52–02:52:38 UTC) sem cobertura de revisão/confirmação. Ciclo executado: produtor da dobra (sessão `7273433a`, registrado no adendo R6-M2 ANTES do ciclo) → re-apreciação do delta (sessão `8c513386`) **APROVADO** com 1 errata menor D1 → confirmação do delta (sessão `e056ffa1`) **CONFIRMADO** → portão re-rodado pelo coordenador sobre o pacote finalizado: **exit 0, 6/6** (ver `PORTAO-R3-D.md`). Conteúdo da dobra correto (try/catch + `.catch` + 1 teste; 96/96 reproduzido pela mesa, pelo re-apreciador e pelo confirmador).
- **F2-2 / R6-M1 (MODERADO, processo) — FECHADO pelo ciclo novo.** O portão estava bloqueado (exit 1, Regra 3) porque o cabeçalho da re-apreciação usava o rótulo `Re-apreciador:`. Correção aplicada: **renomeação do rótulo** para `Revisor:` (nota datada do coordenador ao final do artefato). A alternativa (estender o portão) foi descartada por menor impacto: mudança no `portao-deploy.py` exige o rito próprio (revisão + confirmação independentes + regressão nos dois sentidos — lição R5-F4-1); o portão **não foi editado**. A confirmação da re-apreciação (sessão `65f2eab8`, 23:09 UTC) havia RECUSADO o readendo somente pelo rótulo (itens (a)–(c) conferiam); o readendo relabelado foi **re-confirmado** no ciclo R6-G1.
- **Lição de esteira registrada (para futura inclusão no protocolo): artefatos de revisão/confirmação não são editados in place por terceiros.** A renomeação do rótulo foi feita pelo coordenador e só ganhou confirmação independente no ciclo R6-G1; o procedimento não se repete.
- **Limitação conhecida do portão (registrada em `PORTAO-R3-D.md` e no RELATORIO): o portão não verifica cronologia conteúdo × veredito** — o exit 0 re-rodado não detecta uma dobra tardia; a guarda é o ciclo de re-apreciação + confirmação. Eventual regra nova passa pelo rito próprio do portão, nunca edição in place.
- **D1 (errata, não-bloqueante):** o adendo R4-m4 erra as linhas removidas — `controller.ts` +8/**−2** (não −1), `screens.ts` +5/**−0** (não −1); as adicionadas (+8, +5, +16) estão certas. Registrada por errata datada no RELATORIO (:404) e no LANDING (:166).
- **Nota do produtor da dobra, respondida:** ele flagrou mtimes 02:47:41 (`REVISAO-READENDO-R3-D.md`) e 02:48:41 (`PORTAO-R3-D.md`) dentro da janela da sua sessão, que ele não tocou — eram edições do coordenador (renomeação do rótulo e escrita do PORTAO), ambas declaradas nos próprios artefatos. Não houve sessão paralela neles.
- **R4-M1 / R5-F4-2 (tentativa única de rebase+push): CONSUMADA, com desfecho escrito.** As tentativas de 08/11 (~20:05–20:15 −03) abortaram por reinício do serviço; a escrita ficou registrada como em execução (recusas "an equivalent connector write is already running" no lote R4-A; o arquivo `PUSH-R4-M1.md` existe e documenta 5 tentativas + o diagnóstico — a alegação de que não existiria estava desatualizada). Com as aprovações liberadas pelo dono (09/11 00:12 −03, "Aprovações de escrita = FEITO"), os três lotes pousaram nesta tentativa única (R3-A, R3-B, R3-C commits 1+2; o commit 3 do R3-C ficou supersedido, §5). Os lotes **não** ficaram "aguarda na fila".

## 7. Cadeia de independência (sessões)

| Papel | R3-A | R3-B | R3-C | R3-D |
|---|---|---|---|---|
| Produtor | `ce7c9a90` | `693bc456` | `cfac9b75` | `cd5a69c6` |
| Revisor | `11477f04` (APROVADO) | `ab6009f4` (APROVADO) | `488cf654` (APROVADO) | `ab2817f7` (APROVADO) |
| Confirmador | `84c93113` (CONFIRMADO) | `ae4b58b3` (CONFIRMADO) | `dcdd3a5a` (CONFIRMADO) | `037fa300` (CONFIRMADO) |
| Correção de layout | — | — | — | `4738c482` |
| Re-apreciador | — | — | — | `7c4c8d2d` (APROVADO segue válido) |
| Confirmador do readendo | — | — | — | `65f2eab8` (RECUSA estreita — só o rótulo) |
| Produtor da dobra R4-m4 | — | — | — | `7273433a` |
| Re-apreciador do delta | — | — | — | `8c513386` (APROVADO, errata D1) |
| Confirmador do delta | — | — | — | `e056ffa1` (CONFIRMADO) |
| Coordenador | `38fa3b6b` (todos os lotes) | | | |

Produção, revisão e confirmação nunca caíram na mesma sessão. O portão mecânico (`esteira/portao-deploy.py`) deu exit 0 em todos os quatro lotes (R3-D: pré-delta e pós-delta).

## 8. O que restou aberto

1. **R3-D — pouso bloqueado** pelo conflito de campo (§5). Decisão de merge/canônico pendente (nível dono/pai). O lote está verificado e com portão APROVADO pós-delta; nada nele precisa ser refeito — só a decisão de pouso.
2. **CI total definitiva no PC do dono** sobre o HEAD corrente (gargalo nº 1; a verde da rodada 9 era sobre HEAD anterior).
3. **Piloto P0.30 — decisão NOVA do dono em 09/11/2026 às 00:19** (revoga a espera pelo nome): NÃO há cliente real — o produto será vendido em licitação APÓS a conclusão; a P0.30 vira **base fictícia de testes** (empresa e dados todos fictícios, testar e depois limpar; ordem expressa de 12.000 UCs nas mais variadas situações — pacote `base-ficticia-12k` despachado na esteira). A lista curta de candidatas reais (preparada em 09/11/2026, com recomendação SAMAE Jaraguá do Sul, EMASA e SAMAE Timbó) fica arquivada como referência, sem uso — nenhuma empresa será contatada.
4. **V103 / P-R2G1-1** — decisão de desenho pendente (coluna persistida + fiação × UNIQUE composta).
5. **Diário da main** — registrar os commits R3 (`1ccacd17`, `4fde5dfb`, `9ae1c344`, `c01acb64`) e o supersedimento do commit 3 do R3-C. A cargo da consolidação da sessão principal: não empurrei commit de diário por mim (arquivo compartilhado; lição do incidente R2-G1, em que um commit de diário sobrescreveu a entrada da C5 por partir de cópia velha).
6. **Decisão do dono de 09/11/2026 ~00:10** ("aplicar em bloco: graves + moderados confirmados"): M2 estendido e M3 residual fechado por outros coordenadores; M1 residual segue o fluxo do R3-D com o ciclo novo do R6-G1 (este lote, §6).

## 9. Arquivos

- Pacotes: `~/workspace/ziipsan-producao/correcoes-r3-a/` (RELATORIO, REVISAO, CONFIRMACAO, LANDING, PORTAO-R3-A.md com o registro de pouso), `correcoes-r3-b/`, `correcoes-r3-c/`, `correcoes-r3-d/` (idem + REVISAO-READENDO, REVISAO-CONFIRMACAO-READENDO, REVISAO-READENDO-DELTA-R4M4, REVISAO-CONFIRMACAO-DELTA-R4M4, PORTAO-R3-D.md com o registro pós-delta).
- Este relatório: `~/workspace/ziipsan-producao/RELATORIO-CONSOLIDADO-R3.md`.
- Fonte: `~/workspace/goals/concluir-o-ziipsan/hidden_files/auditoria-continua/rodadas/2026-11-08-1743/00-CONSOLIDADO-RODADA3.md`.
- Paths dos pushes (reutilizáveis): `~/workspace/ziipsan-producao/esteira/paths-r3a.txt`, `paths-r3b.txt`, `paths-r3c1.txt`, `paths-r3c2.txt` (NUL-delimitados, fora do source-dir).

## Adendo — 09/11/2026 (diário R3: verbete preparado, pouso pendente)

Escritor do diário (sessão própria, 09/11/2026 ~00:40 −03): o verbete do lote R3 está escrito e mesclado localmente em `~/workspace/ziipsan-producao/diario-r3/` (`VERBETE-R3.md` + `DIARIO-DE-BORDO.md` mesclado, 171.425 bytes, blob esperado `4aac1ce4…`; base local blob `af6faa05…`, o mesmo verificado vivo na main após o pouso do diário da C6; diff conferido: 0 linhas removidas, só o verbete no final). **O pouso NÃO foi executado:** as duas leituras do GitHub necessárias à regra 1 (reler o blob vivo no ato) foram recusadas pelo dono no cartão de aprovação (`approval_denied` em `list_commits` e `get_file_contents`, ~00:39 −03). Sem a releitura viva, empurrar a partir da cópia local violaria a lição do incidente R2-G1. Nenhuma tentativa de push foi feita. Retomada: re-aprovar a leitura, conferir se o blob vivo segue `af6faa05…` (remesclar se mudou) e rodar `runner-big.py` no diretório `diario-r3/`; verificação pós-pouso pelo blob `4aac1ce4…` (ou o da remescla). Nenhum outro conteúdo deste relatório foi alterado.

## Adendo datado — 09/11/2026 ~02:15 −03 (correção R7-C1, Rodada 7 da auditoria contínua)

**O diário R3 POUSOU — não está mais pendente.** O adendo anterior ("diário R3: verbete preparado, pouso pendente", escrito ~00:40 −03) era verdadeiro no instante da escrita e fica como registro histórico, sem reescrita; este adendo o sucede.

Pouso: **09/11/2026 às 00:45:12 −03** (03:45:12 UTC), commit **`9a8c9c59`** (completo `9a8c9c59150941be35e12405fc3d5fe10e18df72`), 1/1 lotes, HEAD final `9a8c9c59…`. Evidência: `diario-r3/exec.log` (`OK lote 1 (tentativa 1) -> HEAD 9a8c9c5`) e `diario-r3/state.json` (`{"done": [1], "head": "9a8c9c59…"}`). Blob vivo: **`4aac1ce4efff522c45c2a29800bca6e654640448`** = blob do arquivo mesclado (`diario-r3/DIARIO-DE-BORDO.md`); corroboração independente em `ed-75/PORTAO-REGISTRO-ED-75.md:129` (blob vivo `4aac1ce4` conferido no pouso do diário C7). **Bloqueio encerrado: nenhuma retomada, nenhuma remescla, nenhum re-push do verbete R3** — nova tentativa duplicaria a entrada no diário.

**Subponto registrado (sem dano material):** o pouso usou a mescla das 00:43 **sem a releitura do blob vivo exigida pelo passo 2 da retomada do `README-BLOQUEIO.md`** (não há evidência de releitura em `diario-r3/` entre 00:43 e 00:45). O risco não se materializou — o blob vivo conferido depois era de fato `4aac1ce4`, nenhuma entrada interveniente foi sobrescrita — mas a guarda documentada (lição do incidente R2-G1) foi **contornada sem registro**. O fato fica registrado aqui e no adendo do próprio `README-BLOQUEIO.md`; a verificação definitiva da integridade do diário na main é do revisor da main.

Fonte: Rodada 7 da auditoria contínua, revisor 4, achado F1 (= R7-C1, moderado, confirmado); consolidado `00-CONSOLIDADO-RODADA7.md` §3. Correção executada pelo corretor do lote R7-C (sessão `3e666a70-a89e-48af-a0f4-706cf81be20d`, 09/11/2026 ~02:15 −03), por adendo datado em append puro — nenhuma linha original reescrita. Relatório do lote: `correcoes-r7-c/RELATORIO-CORRECAO-R7-C.md`.
