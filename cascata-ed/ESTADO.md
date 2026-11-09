# Estado da Cascata ED — Etapa 2 (produção)

Coordenador: sessão da Etapa 2. Autoridade da ordem: MAPA-CASCATA.md.
Push liberado (regra permanente do dono, 08/11/2026 15:13). HEAD de partida: 864ba6d.

**ESTEIRA COM COORDENADOR-PORTÃO (ordem do dono, 08/11/2026 18:04 — vale para TODOS os pushes daqui em diante):** nenhum pacote pousa sem o coordenador verificar e declarar no relatório da camada: (1) REVISAO com veredito formal de agente distinto do produtor; (2) CONFIRMACAO de agente distinto do produtor E do revisor; (3) rebase verificado contra a main corrente (push não remove linhas alheias — diff de remoções conferido); (4) evidência de testes do pacote com placar. Pacote sem os 4 itens fica na fila com o item faltante nomeado. Quando `~/workspace/ziipsan-producao/esteira/portao-deploy.py` pousar, rodá-lo em cada pacote e anexar a saída ao relatório da camada (em 08/11/2026 18:05 ele e o PROTOCOLO-ESTEIRA.md ainda não existiam — conferir antes de cada leva de pushes).
Placar de partida: ED 50/98 · geral 220/286. Meta: ED 92/98 · geral 262/286 — **ATINGIDA em 09/11/2026 com o fechamento da C9; cascata formalmente fechada (ver seção final).**

## C1 — ✅ FECHADA (diário c4bfa2c; relatório RELATORIO-C1.md)
- ED-41 (onda-A) — ✅ APROVADO (ressalvas D1–D5 corrigidas, confirmação 73/73) e NA MAIN (2 commits → HEAD c43bb0d)
- ED-73 (onda-B) — ✅ APROVADO (ressalvas D1–D5 corrigidas, confirmação independente 53/53) e NA MAIN (2 commits → HEAD 95f1de2)
- MICRO P3.22 N2 → pacote correcoes-n2-p3.22 — ✅ APROVADO (revisor independente, 60/60→64/64, negativo exato) e NA MAIN (commit fix(reporting) → HEAD b56a3a6)

## C2 — ✅ FECHADA (diário 4c357e3; relatório RELATORIO-C2.md)
- ED-10 ✅ (32e94ef) · ED-20 ✅ (f0c5c21) · ED-42 ✅ (4a10f63) · ED-54 ✅ após REPROVAÇÃO corrigida e confirmada (c83ea3f) · ED-101 ✅ (adf13dc)

## C3 — ✅ FECHADA (diário 04c626b; relatório RELATORIO-C3.md)
- ED-23 (A) · ED-43 (B) · ED-50 (B) · ED-56 (B) · ED-58 (A) · ED-66 (B) · ED-91 (B) · ED-92 (B)

## C4 — ✅ FECHADA (diário e54235b; relatório RELATORIO-C4.md) — 10/10 APROVADO COM RESSALVAS → corrigido → CONFIRMADO, nenhum grave
- ED-44 ✅ (f3b839a) · ED-59 ✅ (72ef448) · ED-62 ✅ (df6155d) · ED-71 ✅ (22b2ebe) · ED-72 ✅ (e80b3d9) · ED-77 ✅ (150a3b6) · ED-85 ✅ (05cfbcf) · ED-87 ✅ (7a9a0d6, 5725e19) · ED-89 ✅ (5b21abd) · ED-96 ✅ (5c9c827)
- Pendências nomeadas da camada: ver RELATORIO-C4.md (P-ED71-R1/R2, P-ED44-01/02/03, P-ED85-01, P-ED89-02 ampliada, P-ED77-01..06)

## C5 — ✅ FECHADA (diário 9d1acf6 + reparos convergentes 4f8e2e7/83a9539; relatório RELATORIO-C5.md) — 10/10 APROVADO COM RESSALVAS → corrigido → CONFIRMADO → pousado → verificação pós-pouso 225/225
- ED-49 ✅ (2dc0969) · ED-78 ✅ (62fbe07, b2cd987) · ED-79 ✅ (1d9a947, 497c091) · ED-81 ✅ (47e692b; REPROVADA → correção → reapreciação → confirmação complementar) · ED-82 ✅ (7ca5e4e) · ED-86 ✅ (94b9707) · ED-90 ✅ (4de18cc, 023489b) · ED-94 ✅ (f6fb11a) · ED-95 ✅ (e27e961, caa78bf) · ED-105 ✅ (2f70e4e, ef643ec)
- Fechamento formal (verificação pós-pouso, portão mecânico 10/10, relatório, ESTADO, diário) executado pelo COORDENADOR SUCESSOR — o anterior encerrou após o pouso da ED-79 sem os atos de fechamento; nenhum pouso precedeu a sua confirmação (mtimes conferidos).
- Colisão de diário com o lote paralelo R2-G1 registrada e resolvida por reparos convergentes dos dois portões (detalhe: RELATORIO-C5.md §6). Placar: ED 85/98, geral 255/286.

## C6 — ✅ FECHADA (relatório RELATORIO-C6.md) — 4/4 APROVADO → confirmado → pousado → verificação pós-pouso 91/91 · ⚠️ diário da camada PREPARADO, NÃO POUSADO (pendência P-C6-DIARIO: aprovações de escrita expiraram sem decisão; operação pronta em push/diario-c6/)
- ED-80 ✅ (386cec3) · ED-83 ✅ (af04605, 261402f; 2 confirmações) · ED-70 ✅ (e14ee3d; REPROVADA → correção → reapreciação → confirmação) · ED-93 ✅ (fde45c5; idem)
- Fechamento formal (verificação pós-pouso 91/91, portão mecânico reexecutado, relatório, ESTADO; diário preparado mas NÃO pousado — P-C6-DIARIO) executado pelo COORDENADOR DE FECHAMENTO C5+C6 — os pousos já estavam completos na main (o da ED-93, deixado "em segundo plano" pelo antecessor, pousou: fde45c5).
- Desvio de forma do Portão na ED-70 DECLARADO: pouso executado com o bloqueio mecânico (falso positivo P-ESTEIRA-01) ainda por demonstrar em artefato; demonstração lavrada depois (PORTAO-REGISTRO-ED-70.md) e desvio registrado no RELATORIO-C6.md §3 e no diário. Na ED-93, a demonstração precedeu o pouso (§3.2 cumprido). Placar: ED 89/98, geral 259/286.

## C7 — ✅ POUSADA (ED-75 na main; pendência P-C7-POUSO FECHADA em 09/11/2026)
- ED-75 (B) — produção → revisão APROVADO (D1/D2 menores) → correção → reapreciação CONFIRMADA → confirmação CONFIRMADO → portão mecânico APROVADO, rebase verificado (HEAD fde45c5 = base; 21 arquivos novos, ausência remota conferida). Dobra R5-A (F1-B1/B2/B3) e dobra R6-A (R6-A1..A5) ao pacote local, cada uma com reapreciação + confirmação do delta e portão re-aprovado. **Pouso executado 09/11/2026 com as aprovações liberadas pelo dono (00:12 −03): commit `625eb060` (HEAD no ato `82bad7ce`; ausência remota repetida; 21 arquivos, 100.848 bytes), verificação pós-pouso 21/21 byte-idênticos; diário da C7 pousado (`76d438dd`, blob `e33143af`).** JUnit 47/47, PG 14/14 negativas. Placar após o pouso: ED 90/98, geral 260/286. Registro completo: ed-75/PORTAO-REGISTRO-ED-75.md (adendos R5-A, R6-A e pouso)

## C8 — ✅ FECHADA (diário 6625ed7; relatório RELATORIO-C8.md)
- ED-97 (B) — ✅ APROVADO COM RESSALVAS (D1 moderado + D2–D5 menores) → correção por construção → reapreciação APROVADO → CONFIRMADO → portão mecânico APROVADO (exit 0) → NA MAIN (commit 4e808ac; verificação pós-pouso 17/17 byte-idênticos). JUnit 45/45, PG 4/4 negativas. **P-ED75-01 RESOLVIDA** (escopo do regulador da ED-75 definido nesta entrega). Pendências nomeadas da camada: P-ED97-01..05 (ver RELATORIO-C8.md). Placar: ED 91/98, geral 261/286.

## C9 — ✅ FECHADA (diário f659bad8; relatório RELATORIO-C9.md)
- ED-98 (B) — ✅ APROVADO COM RESSALVAS (D1, D2, D4 moderados; D3, D5, D6, D7 menores) → correção por construção (sondas S1–S8 viraram negativos permanentes) → reapreciação APROVADO (44/44) → CONFIRMADO → NA MAIN (commit 02090935; lote único, 17 arquivos, 90.349 bytes; verificação pós-pouso 17/17 byte-idênticos). JUnit 39/39, PG 12/12 negativas. Portão mecânico bloqueado na Regra 3 por falso positivo do extrator (cabeçalho da reapreciação sem o token literal exigido) — resolvido pela rota do §3.2, demonstração e verificação manual em PORTAO-REGISTRO-ED-98.md; portão não editado. Pendências nomeadas da camada: P-ED98-01 (ampliada) a P-ED98-07. Placar: ED 92/98, geral 262/286.

## CASCATA ED — ✅ FORMALMENTE FECHADA (09/11/2026)
As 9 camadas (C1–C9) e as 42 tarefas estão produzidas, aprovadas sob a esteira com coordenador-portão, pousadas na main e verificadas pós-pouso por hash. **Placar final: ED 92/98 · geral 262/286 — META ATINGIDA.** Fecho consolidado (o que as camadas entregaram + pendências sobreviventes de todas as camadas, agrupadas): RELATORIO-C9.md §2–§4. Fora da cascata, inalterado: ED-01 (raiz externa) e ED-51, ED-68, ED-102, ED-103, ED-104 (sob demanda do dono).

## Registro de pushes
- C1/N2: 864ba6d → b56a3a6 (1 commit, 2 arquivos)
- C1/ED-73: b56a3a6 → 95f1de2 (2 commits, 17 arquivos)
- C1/ED-41: 95f1de2 → c43bb0d (2 commits, 36 arquivos)
- C1/diário: c43bb0d → c4bfa2c (docs(diario))
- C2/ED-10: c4bfa2c → 32e94ef (1 commit, 21 arquivos)
- C2/ED-20: 32e94ef → f0c5c21 (1 commit, 26 arquivos)
- C2/ED-101: f0c5c21 → adf13dc (1 commit, 24 arquivos)
- C2/ED-42: adf13dc → 4a10f63 (2 commits, 21 arquivos)
- C2/ED-54: 4a10f63 → c83ea3f (2 commits, 24 arquivos)
- C2/diário: c83ea3f → 4c357e3 (docs(diario))
- C3/ED-91: 4c357e3 → 01f7042 (1 commit, 14 arquivos)
- C3/ED-23: 01f7042 → ad131fb (1 commit, 19 arquivos)
- C3/ED-66: ad131fb → ef8c2a9 (2 commits, 21 arquivos)
- C3/ED-50: ef8c2a9 → dd0fe28 (2 commits, 30 arquivos)
- C3/ED-58: dd0fe28 → 0dcce85 (2 commits, 30 arquivos)
- C3/ED-92: 0dcce85 → 52833b0 (1 commit, 16 arquivos)
- C3/ED-43: 52833b0 → c5bf750 (1 commit, 29 arquivos)
- (mesa de auditoria pousou 785490d entre os pousos da C3 — HEAD lido a cada lote, sem corrida)
- C3/ED-56: 785490d → c97f534 (2 commits, 30 arquivos)
- C3/diário: c97f534 → 04c626b (docs(diario)) — C3 FECHADA, relatório RELATORIO-C3.md; placar ED 65/98, geral 235/286
- C4 (pousos desta sessão; entre eles, a mesa de auditoria pousou o lote da rodada 2 até c72aa5f — HEAD lido a cada lote, sem corrida):
  - C4/ED-71: → 22b2ebe · C4/ED-44: → f3b839a · C4/ED-62: → df6155d · C4/ED-59: → 72ef448 · C4/ED-72: → e80b3d9
  - C4/ED-96: → 5c9c827 · C4/ED-89: → 5b21abd (2 lotes) · C4/ED-85: → 05cfbcf · C4/ED-77: → 150a3b6
  - C4/ED-87: c72aa5f → 7a9a0d6 → 5725e19 (2 lotes, 22 arquivos)
  - C4/diário: 5725e19 → e54235b (docs(diario)) — C4 FECHADA, relatório RELATORIO-C4.md; placar ED 75/98, geral 245/286
- C5 (pousos do coordenador anterior; entre eles e o fechamento, o lote R2-G1 pousou f6641b6/aa4d961 e o seu diário 640f92e — ver colisão no RELATORIO-C5 §6):
  - C5/ED-94: e54235b → f6fb11a · C5/ED-86: → 94b9707 · C5/ED-105: → 2f70e4e → ef643ec · C5/ED-82: → 7ca5e4e · C5/ED-90: → 4de18cc → 023489b
  - C5/ED-81: → 47e692b · C5/ED-78: → 62fbe07 → b2cd987 · C5/ED-95: → e27e961 → caa78bf · C5/ED-49: → 2dc0969 · C5/ED-79: → 1d9a947 → 497c091
  - C5/diário: (base aa4d961) → 9d1acf6 (docs(diario)); reparo de colisão → 4f8e2e7; reparo convergente do Portão R2-G1 → 83a9539 — C5 FECHADA, relatório RELATORIO-C5.md; placar ED 85/98, geral 255/286
- C6 (pousos do coordenador sucessor; fechamento formal pelo coordenador de fechamento C5+C6):
  - C6/ED-80: 83a9539 → 386cec3 (1 lote, 20 arquivos) · C6/ED-83: → af04605 → 261402f (2 lotes, 25 arquivos)
  - C6/ED-70: → e14ee3d (1 lote, 22 arquivos; desvio de forma do Portão declarado no RELATORIO-C6 §3) · C6/ED-93: → fde45c5 (1 lote, 24 arquivos; demonstração P-ESTEIRA-01 prévia ao pouso)
  - C6/diário: PREPARADO, NÃO POUSADO — operação em push/diario-c6/ (base viva 23b5ab39…); 2 tentativas (23:02/23:34 −03) pararam por timeout de aprovação de escrita; HEAD segue fde45c5. Pendência P-C6-DIARIO (ver RELATORIO-C6.md §6). Camada fechada em produção/portão/pousos/verificação; placar ED 89/98, geral 259/286
  - C6/diário — ADENDO 09/11/2026: **P-C6-DIARIO RESOLVIDA** — pouso executado com aprovações liberadas pelo dono: commit `4d88b89` (HEAD no ato `1ccacd1`, R3-A em paralelo; blob vivo seguia `23b5ab39…`, sem remescla; diff 0 remoções/+13 linhas); verificação pós-pouso: blob vivo `af6faa05…` = git blob SHA do arquivo pousado. Ver RELATORIO-C6.md §6 (adendo).
- C7 (coordenador da camada C7; ciclo completo até o estágio 6, pouso pendente):
  - C7/ED-75: **POUSADO em 09/11/2026** — commit `625eb060` (HEAD no ato `82bad7ce`; 21 arquivos, 100.848 bytes; verificação pós-pouso 21/21 byte-idênticos). Deltas R5-A e R6-A dobrados antes do pouso, cada um com reapreciação + confirmação e portão re-aprovado (JUnit 47/47, PG 14/14 negativas). C7/diário: `76d438dd` (docs(diario), blob `e33143af`). **P-C7-POUSO FECHADA.** Placar após o pouso: ED 90/98, geral 260/286. Ver RELATORIO-C7.md (adendo de pouso).
- C8 (coordenador da camada C8; ciclo completo sob a esteira com portão mecânico):
  - C8/ED-97: **POUSADO em 09/11/2026** — commit `4e808ac` (HEAD no ato `2d211228`; 17 arquivos, 59.303 bytes, lote único; verificação pós-pouso 17/17 byte-idênticos por blob SHA-1). Cadeia: revisão APROVADO COM RESSALVAS (D1 moderado, D2–D5 menores) → correção por construção (JUnit 45/45) → reapreciação APROVADO (45 sondas) → confirmação CONFIRMADO → portão mecânico APROVADO (exit 0, regras 1–6). C8/diário: `6625ed7` (docs(diario), blob verificado `fc1b8b06`). **P-ED75-01 RESOLVIDA.** Placar após o pouso: ED 91/98, geral 261/286. Ver RELATORIO-C8.md.
- C9 (coordenador da camada C9; ciclo completo sob a esteira com coordenador-portão):
  - C9/ED-98: **POUSADO em 09/11/2026** — commit `02090935` (HEAD no ato `b143cba6`, main andando com os lotes da base fictícia em caminhos disjuntos; 17 arquivos novos, 90.349 bytes, lote único; verificação pós-pouso 17/17 byte-idênticos por blob SHA-1). Cadeia: revisão APROVADO COM RESSALVAS (D1/D2/D4 moderados; D3/D5/D6/D7 menores) → correção por construção (JUnit 39/39; PG 12/12) → reapreciação APROVADO (44/44) → confirmação CONFIRMADO → portão: 4 itens declarados; Regra 3 mecânica com falso positivo demonstrado (§3.2, PORTAO-REGISTRO-ED-98.md). C9/diário: `f659bad8` (docs(diario), blob verificado `12d1b19e` = mescla). **CASCATA FECHADA.** Placar após o pouso: ED 92/98, geral 262/286. Ver RELATORIO-C9.md.

## Nota datada — 09/11/2026 ~02:15 −03 (regra R7-C3, Rodada 7 da auditoria contínua)

**Regra prospectiva (não reabre C6/C7/C8):** nas próximas camadas, **arquivar a tabela hash a hash da verificação pós-pouso junto ao relatório da camada ou ao PORTAO-REGISTRO**, em vez de só afirmar o placar em prosa. Origem: as verificações 91/91 (C6), 21/21 (C7) e 17/17 (C8) estão registradas com método e commits, mas sem tabela por arquivo arquivada — afirmação crível (método descrito, somas conferem, corroborações cruzadas existem), mas não reproduzível a partir dos arquivos locais. As três camadas **não** são reabertas por isso. Fonte: revisor 4, achado F3 (= R7-C3, menor, confirmado); consolidado `00-CONSOLIDADO-RODADA7.md` §3. Registro: corretor do lote R7-C (sessão `3e666a70-a89e-48af-a0f4-706cf81be20d`). Relatório do lote: `correcoes-r7-c/RELATORIO-CORRECAO-R7-C.md`.
