# CONFIRMAÇÃO — Lote R11-A (auditoria contínua do ZiipSan, Rodada 11, 09/11/2026)

- **Confirmador (sessão):** `f3d47244-9701-46c9-bcd5-3f6e42028a79`
- **Produtor do lote (sessão):** `d0db0ab0-2a43-46b8-96a5-3c1cef6e1a47`
- **Revisor do lote (sessão):** `443a1e1a-30ca-4be1-a2c1-578072355ecc`
- **Data:** 2026-11-09
- **Fonte do achado:** `~/workspace/goals/concluir-o-ziipsan/hidden_files/auditoria-continua/rodadas/2026-11-09-0943/00-CONSOLIDADO-RODADA11.md` (§3, R11-EX-01), lido antes de começar.
- **Trabalho:** 100% local, somente leitura sobre os originais; **zero chamadas ao GitHub** (parada dura por HTTP 429). `DECISOES-DO-DONO.md` e o portão (`portao-deploy.py`) não foram tocados por esta confirmação. `push/state.json` da raiz e `push/espera/state.json` não foram tocados nem lidos.

## Veredito formal

**CONFIRMADO — 0 divergências, 0 ressalvas.**

O lote R11-A executa exactamente o repasse da mesa da Rodada 11 (item único R11-EX-01): um adendo de uma linha ao `LANDING.md` do R10-A, sem reescrita de originais. Todos os hashes, greps e a reconstrução do estado pré-preenchimento foram recomputados por mim nesta sessão e conferem com o declarado pelo produtor e pelo revisor. Nenhum dado inventado, nenhum número sem fonte.

## 1. Hashes recomputados por mim (SHA-256)

| Ficheiro | SHA-256 recomputado | Declarado / registado | Resultado |
|---|---|---|---|
| `correcoes-r10-a/LANDING.md` | `574130a77feff7a32bef2954185a09c931a3086bf787581f6f14b613100a4026` | brief + consolidado R10-A (linha 22: "LANDING (actual)") + relatório e revisão do R11-A | **CONFERE** |
| `correcoes-r10-a/ADENDO-R11-EX-01-2026-11-09.md` | `5acad06f6728b0640394da56763199a01a9e6f6a9ba8880c7e9cc7fe941e7612` | `RELATORIO-R11-A.md` + `REVISAO-R11-A.md` | **CONFERE** |
| `correcoes-r11-a/REVISAO-R11-A.md` | `06965e13bc46ce745f4e0a2e7e29cd2089b9cfc0818fde36306c680f478de55d` | brief | **CONFERE** |
| `correcoes-r11-a/RELATORIO-R11-A.md` | `a5cbce9cc08edc90d9691991a625f0d8abcfc1c082589c867de6df498f1e8da4` | (valor de referência desta confirmação; não declarado noutro artefacto) | registado |
| `correcoes-r10-a/RELATORIO-R10-A.md` | `11f33ca8994786c32859913adfaf12a6eadbe6081d1fbbcb3c72acb35f2bac49` | tabela do LANDING + tabela do consolidado + revisão do R11-A | **CONFERE** |
| `correcoes-r10-a/REVISAO-R10-A.md` | `cb94c3961eb1a5fb336281f3f909c41a0e79fb616defac3571a7232c63764bf0` | tabela do LANDING + tabela do consolidado + revisão do R11-A | **CONFERE** |
| `correcoes-r10-a/CONFIRMACAO-R10-A.md` | `a8ea8b7751aef0bf14f227d5bd1387da75b59a3a0bb3fba337303bd1f3b87375` | tabela do LANDING + tabela do consolidado + revisão do R11-A | **CONFERE** |
| `correcoes-r10-a/PORTAO-REGISTRO-R10-A.md` | `af4a35449df14ffd70466fbe72f605eb973f3873a139ae91e32f714ee1640c07` | consolidado R10-A (linha 22: "registo") + revisão do R11-A | **CONFERE** |
| `correcoes-r10-a/portao-saida.txt` | `2cfeecaf9ef3552afc88530ebcf073a229e567c4adfe789f969b25b6ef42a10e` | consolidado R10-A ("Saída mecânica integral…") + revisão do R11-A | **CONFERE** |
| `correcoes-r10-a/RELATORIO-CONSOLIDADO-R10-A.md` | `52bf873f231fc8fd95dc2cd27216732fc697f3b018b32b1135b8a6b0832889b1` | revisão do R11-A (valor de referência) | **CONFERE** |
| `esteira/portao-deploy.py` | `05b58ba7378a2b8b3978feb2f7c5da240701a32c960b248fb3dc74352967cc16` | consolidado da Rodada 11 §4 (portão não editado) | **CONFERE** |

## 2. Leitura do adendo na íntegra

Lido na íntegra (`ADENDO-R11-EX-01-2026-11-09.md`, 1.405 bytes — confere com o tamanho registado pelo revisor):

- **Declaração:** "O valor a conferir no pós-pouso para o próprio ficheiro `LANDING.md` do pacote `correcoes-r10-a/` é `574130a77feff7a32bef2954185a09c931a3086bf787581f6f14b613100a4026` (SHA-256)." — **exacto**: é o SHA-256 actual do ficheiro, recomputado por mim (§1). É exactamente o repasse da mesa (§3 do consolidado da Rodada 11).
- **Esclarecimento:** o valor `61786ae8c43118d3ffd4fdf40c197e6025dd6d18d9ecb0dfe2fb83aca35ffab8` da tabela do `LANDING.md` (linha 25) é o SHA-256 do estado pré-preenchimento da célula auto-referencial (placeholder `a registar após o portão`, sem backticks); "Não é o valor a conferir no pós-pouso; o valor a conferir é o `574130a7…` declarado acima." — **exacto**: a reconstrução independente feita por mim (§4) devolve exactamente `61786ae8…`.
- **Integridade:** "Nenhum original foi reescrito por este adendo: o `LANDING.md` do `correcoes-r10-a/` e o consolidado do R10-A mantêm-se intactos." — **verdadeiro** (§3).
- O adendo identifica correctamente o autor (sessão produtora `d0db0ab0-2a43-46b8-96a5-3c1cef6e1a47`), o item (R11-EX-01) e a data. Nenhum dado inventado, nenhum número sem fonte.

## 3. Nenhum original reescrito

- **LANDING.md do R10-A:** SHA actual `574130a7…` — igual ao valor que o próprio consolidado do R10-A já registava como "actual" (linha 22, lida directamente por mim) e igual ao valor antes da escrita do adendo, conforme o relatório de produção. mtime `2026-11-09 11:01:06 UTC`, anterior à produção deste lote (adendo 12:54:23 UTC).
- **RELATORIO-CONSOLIDADO-R10-A.md:** SHA `52bf873f…`, mtime `2026-11-09 11:01:24 UTC` — anterior à produção deste lote; a linha 22 foi lida directamente e regista ambos os valores do LANDING (`61786ae8…` valor da linha · `574130a7…` actual), como a mesa afirmou.
- **Restantes ficheiros do pacote R10-A** (`RELATORIO-R10-A.md`, `REVISAO-R10-A.md`, `CONFIRMACAO-R10-A.md`, `PORTAO-REGISTRO-R10-A.md`, `portao-saida.txt`): hashes todos conferem com os registados (§1); mtimes entre 10:53 e 11:00 UTC, anteriores à produção deste lote — a afirmação do revisor sobre esse intervalo está correcta para os cinco ficheiros listados.
- **Único ficheiro novo na pasta `correcoes-r10-a/` após as 12:00 UTC:** o adendo (`2026-11-09 12:54:23 UTC`), confirmado por `find -newermt '2026-11-09 12:00:00 UTC'`.
- **Portão intacto:** `portao-deploy.py` SHA `05b58ba7…`, mtime `2026-11-09 00:14:29 UTC` (inalterado).

## 4. Reconstrução independente do estado pré-preenchimento

Cópia do `LANDING.md` para `/tmp/r11a-conf-reconstrucao.md`, linha 25 substituída pelo placeholder sem backticks:

`| \`LANDING.md\` (este ficheiro) | a registar após o portão |`

SHA-256 da reconstrução: `61786ae8c43118d3ffd4fdf40c197e6025dd6d18d9ecb0dfe2fb83aca35ffab8` — **CASA exactamente** com o valor da tabela do LANDING (linha 25) e com o valor declarado no adendo, no relatório e na revisão. Quarta sessão a reconstruir o mesmo valor (mesa da Rodada 11, produtor, revisor, confirmador).

## 5. Contagens por grep no LANDING.md do R10-A

- Ocorrências de `574130a7`: **0** — confere com o brief, com o relatório de produção e com a revisão (o LANDING não declara no seu próprio texto o valor a conferir; é precisamente o ponto de guarda do R11-EX-01, agora suprido pelo adendo).
- Ocorrências de `61786ae8`: **1**, na linha 25 — confere.

## 6. Separação de papéis e vereditos formais

- Produtor `d0db0ab0-2a43-46b8-96a5-3c1cef6e1a47` ≠ revisor `443a1e1a-30ca-4be1-a2c1-578072355ecc` ≠ confirmador `f3d47244-9701-46c9-bcd5-3f6e42028a79` — **três sessões distintas**, confirmadas nos cabeçalhos dos três artefactos.
- Produção: **CONCLUÍDO — 1/1 item** (R11-EX-01) — presente no `RELATORIO-R11-A.md`.
- Revisão: **APROVADO — 0 divergências** — presente no `REVISAO-R11-A.md`.

## 7. Relatório de produção e revisão — verificação ponto a ponto

- **Relatório (`RELATORIO-R11-A.md`):** sessão produtora, SHA do adendo `5acad06f…`, SHA do LANDING `574130a7…`, greps (0 × `574130a7`; 1 × `61786ae8`, linha 25), reconstrução com SHA `61786ae8…` (mesmo placeholder que usei), afirmações "LANDING.md não alterado" e "nenhum original reescrito" — **todas conferem** com o que recomputei. A afirmação de que `DECISOES-DO-DONO.md`, o portão e os dois `state.json` não foram tocados é consistente com o âmbito local deste lote; o portão foi confirmado intacto por hash e mtime (§1, §3). (Os `state.json` não foram lidos por esta confirmação, por ordem expressa do brief — não ler como estado corrente.)
- **Revisão (`REVISAO-R11-A.md`):** os 8 hashes da sua tabela (§1) conferem todos com os meus (§1); o tamanho do adendo (1.405 bytes) confere; as leituras do adendo, das mtimes e da reconstrução conferem; a nota de contexto sobre a guarda 2 do LANDING é correcta e não altera o veredito. **0 divergências materiais** entre a revisão e o que verifiquei.

## 8. Ressalvas

Nenhuma.

## 9. Resumo

- **Veredito: CONFIRMADO — 0 divergências, 0 ressalvas.**
- Adendo: `5acad06f6728b0640394da56763199a01a9e6f6a9ba8880c7e9cc7fe941e7612`
- LANDING do R10-A (actual, valor a conferir no pós-pouso): `574130a77feff7a32bef2954185a09c931a3086bf787581f6f14b613100a4026`
- LANDING do R10-A (valor da linha 25, estado pré-preenchimento, reconstruído): `61786ae8c43118d3ffd4fdf40c197e6025dd6d18d9ecb0dfe2fb83aca35ffab8`
- Revisão: `06965e13bc46ce745f4e0a2e7e29cd2089b9cfc0818fde36306c680f478de55d`
- Originais intactos; portão intacto; zero chamadas ao GitHub; pouso do lote segue pendente da janela pós-429.
