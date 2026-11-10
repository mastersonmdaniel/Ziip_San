# REVISÃO — Lote R11-A (auditoria contínua do ZiipSan, Rodada 11, 09/11/2026)

- **Revisor (sessão):** `443a1e1a-30ca-4be1-a2c1-578072355ecc`
- **Produtor do lote (sessão):** `d0db0ab0-2a43-46b8-96a5-3c1cef6e1a47`
- **Data:** 2026-11-09
- **Fonte do achado:** `~/workspace/goals/concluir-o-ziipsan/hidden_files/auditoria-continua/rodadas/2026-11-09-0943/00-CONSOLIDADO-RODADA11.md` (§3, R11-EX-01), lido antes de começar.
- **Trabalho:** 100% local, somente leitura sobre os originais; **zero chamadas ao GitHub** (parada dura por HTTP 429). `DECISOES-DO-DONO.md` e o portão (`portao-deploy.py`) não foram tocados por esta revisão. `push/state.json` da raiz e `push/espera/state.json` não foram tocados.

## Veredito formal

**APROVADO — 0 divergências.**

O lote R11-A executa exactamente o repasse da mesa da Rodada 11 (item único R11-EX-01): um adendo de uma linha ao `LANDING.md` do R10-A, sem reescrita de originais. Todos os hashes, greps e a reconstrução do estado pré-preenchimento foram recomputados por mim nesta sessão e conferem com o declarado pelo produtor. Nenhum dado inventado, nenhum número sem fonte, nenhuma afirmação insustentável no adendo nem no relatório de produção.

## 1. Hashes recomputados por mim (SHA-256)

| Ficheiro | SHA-256 recomputado | Declarado / registado | Resultado |
|---|---|---|---|
| `correcoes-r10-a/LANDING.md` | `574130a77feff7a32bef2954185a09c931a3086bf787581f6f14b613100a4026` | brief + consolidado R10-A (linha 22: "LANDING (actual)") | **CONFERE** |
| `correcoes-r10-a/ADENDO-R11-EX-01-2026-11-09.md` | `5acad06f6728b0640394da56763199a01a9e6f6a9ba8880c7e9cc7fe941e7612` | `RELATORIO-R11-A.md` | **CONFERE** |
| `correcoes-r10-a/RELATORIO-R10-A.md` | `11f33ca8994786c32859913adfaf12a6eadbe6081d1fbbcb3c72acb35f2bac49` | tabela do LANDING + tabela do consolidado | **CONFERE** |
| `correcoes-r10-a/REVISAO-R10-A.md` | `cb94c3961eb1a5fb336281f3f909c41a0e79fb616defac3571a7232c63764bf0` | tabela do LANDING + tabela do consolidado | **CONFERE** |
| `correcoes-r10-a/CONFIRMACAO-R10-A.md` | `a8ea8b7751aef0bf14f227d5bd1387da75b59a3a0bb3fba337303bd1f3b87375` | tabela do LANDING + tabela do consolidado | **CONFERE** |
| `correcoes-r10-a/PORTAO-REGISTRO-R10-A.md` | `af4a35449df14ffd70466fbe72f605eb973f3873a139ae91e32f714ee1640c07` | consolidado R10-A (linha 22: "registo") | **CONFERE** |
| `correcoes-r10-a/portao-saida.txt` | `2cfeecaf9ef3552afc88530ebcf073a229e567c4adfe789f969b25b6ef42a10e` | consolidado R10-A ("Saída mecânica integral…") | **CONFERE** |
| `correcoes-r10-a/RELATORIO-CONSOLIDADO-R10-A.md` | `52bf873f231fc8fd95dc2cd27216732fc697f3b018b32b1135b8a6b0832889b1` | (não registado noutro artefacto do pacote; valor de referência desta revisão) | registado |

## 2. Leitura do adendo na íntegra

Lido na íntegra (`ADENDO-R11-EX-01-2026-11-09.md`, 1.405 bytes):

- **Declaração:** "O valor a conferir no pós-pouso para o próprio ficheiro `LANDING.md` do pacote `correcoes-r10-a/` é `574130a77feff7a32bef2954185a09c931a3086bf787581f6f14b613100a4026` (SHA-256)." — **exacto**: é o SHA-256 actual do ficheiro, recomputado por mim (§1).
- **Esclarecimento:** o valor `61786ae8c43118d3ffd4fdf40c197e6025dd6d18d9ecb0dfe2fb83aca35ffab8` da tabela do `LANDING.md` (linha 25) é o SHA-256 do estado pré-preenchimento da célula auto-referencial (placeholder `a registar após o portão`, sem backticks); "Não é o valor a conferir no pós-pouso; o valor a conferir é o `574130a7…` declarado acima." — **exacto**: a reconstrução independente feita por mim (§4) devolve exactamente `61786ae8…`.
- **Integridade:** "Nenhum original foi reescrito por este adendo: o `LANDING.md` do `correcoes-r10-a/` e o consolidado do R10-A mantêm-se intactos." — **verdadeiro** (§3).
- O texto é fiel ao repasse da mesa (adendo de uma linha, sem reescrita de originais). Nenhum dado inventado, nenhum número sem fonte, nenhuma afirmação que não se sustente. O adendo identifica correctamente o autor (sessão produtora `d0db0ab0-2a43-46b8-96a5-3c1cef6e1a47`), o item (R11-EX-01) e a data.

## 3. Nenhum original reescrito

- **LANDING.md do R10-A:** SHA actual `574130a7…` — igual ao valor que o próprio consolidado do R10-A já registava como "actual" (linha 22) e igual ao valor antes da escrita do adendo, conforme o relatório de produção. mtime `2026-11-09 11:01:06 UTC`, anterior à produção deste lote.
- **RELATORIO-CONSOLIDADO-R10-A.md:** SHA `52bf873f…`, mtime `2026-11-09 11:01:24 UTC` — anterior à produção deste lote; o seu conteúdo (incl. a linha 22, que regista ambos os valores do LANDING) foi lido directamente e está intacto.
- **Restantes ficheiros do pacote R10-A** (`RELATORIO-R10-A.md`, `REVISAO-R10-A.md`, `CONFIRMACAO-R10-A.md`, `PORTAO-REGISTRO-R10-A.md`, `portao-saida.txt`): hashes todos conferem com os registados (§1); mtimes entre 10:53 e 11:00 UTC, anteriores à produção deste lote.
- **Único ficheiro novo de hoje na pasta `correcoes-r10-a/`:** o adendo (`2026-11-09 12:54:23 UTC`), confirmado por `find -newermt '2026-11-09 12:00:00 UTC'`.
- **Portão intacto:** `portao-deploy.py` mtime `2026-11-09 00:14:29 UTC` (inalterado).

## 4. Reconstrução independente do estado pré-preenchimento

Cópia do `LANDING.md` para `/tmp/r11a-rev-reconstrucao.md`, linha 25 substituída pelo placeholder sem backticks:

`| \`LANDING.md\` (este ficheiro) | a registar após o portão |`

SHA-256 da reconstrução: `61786ae8c43118d3ffd4fdf40c197e6025dd6d18d9ecb0dfe2fb83aca35ffab8` — **CASA exactamente** com o valor da tabela do LANDING (linha 25) e com o valor declarado no adendo e no relatório de produção. Confirmação independente (terceira sessão a reconstruir o mesmo valor, após a mesa da Rodada 11 e o produtor deste lote).

## 5. Contagens por grep no LANDING.md do R10-A

- Ocorrências de `574130a7`: **0** — confere com o brief e com o relatório de produção (o LANDING não declara no seu próprio texto o valor a conferir; é precisamente o ponto de guarda do R11-EX-01, agora suprido pelo adendo).
- Ocorrências de `61786ae8`: **1**, na linha 25 — confere.

## 6. Relatório de produção (`RELATORIO-R11-A.md`)

Verificado ponto a ponto contra o que recomputei:

- Sessão produtora `d0db0ab0-2a43-46b8-96a5-3c1cef6e1a47` — confere com o adendo e com o brief.
- SHA do adendo `5acad06f…` — confere (§1).
- SHA do LANDING `574130a7…` — confere (§1).
- Greps (0 × `574130a7`; 1 × `61786ae8`, linha 25) — conferem (§5).
- Reconstrução com SHA `61786ae8…` — reproduzida por mim com resultado idêntico (§4). O comando/placeholder descrito no relatório (`| \`LANDING.md\` (este ficheiro) | a registar após o portão |`) é o mesmo que usei.
- Afirmação "LANDING.md não alterado" e "nenhum original reescrito" — verdadeiras (§3).
- Afirmação de que `DECISOES-DO-DONO.md`, o portão e os dois `state.json` não foram tocados — consistente com o âmbito local deste lote; o portão foi confirmado intacto por mtime (§3). (Os `state.json` não foram lidos por esta revisão, por ordem expressa do brief — não ler como estado corrente.)
- **0 divergências materiais** entre o relatório e o que verifiquei.

## 7. Achados e ressalvas

Nenhum achado. Nenhuma ressalva.

Nota de contexto (sem acção): a guarda 2 do próprio `LANDING.md` do R10-A ("cada ficheiro da tabela acima conferido por SHA-256 contra os valores declarados") continua a ler-se literalmente divergente sobre a sua própria linha 25 — o que o adendo agora resolve por remissão expressa: no acto do pouso, o valor a conferir para o `LANDING.md` é `574130a7…`, e o `61786ae8…` da tabela é o estado pré-preenchimento documentado. O adendo viaja com o pacote; o LANDING não foi reescrito, por desenho do repasse.

## 8. Resumo

- **Veredito: APROVADO — 0 divergências.**
- Adendo: `5acad06f6728b0640394da56763199a01a9e6f6a9ba8880c7e9cc7fe941e7612`
- LANDING do R10-A (actual): `574130a77feff7a32bef2954185a09c931a3086bf787581f6f14b613100a4026`
- LANDING do R10-A (valor da linha 25, estado pré-preenchimento, reconstruído): `61786ae8c43118d3ffd4fdf40c197e6025dd6d18d9ecb0dfe2fb83aca35ffab8`
- Originais intactos; portão intacto; zero chamadas ao GitHub.
