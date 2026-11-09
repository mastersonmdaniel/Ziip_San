# Confirmação do Lote R7-B (base fictícia 12k — registos/documentação)

**Confirmador do lote R7-B (sessão `48bd52df-a04e-4225-987a-17d84fa6bc06`).**
**Data:** 09/11/2026.
**Corretor do lote:** sessão `b6cf0c91-9a27-46d5-a359-d4a9ddcaecb9`
(`correcoes-r7-b/RELATORIO-CORRECAO-R7-B.md`).
**Revisor do lote:** sessão `4fa0183b-915a-4029-9eb9-5a9d8d97e134`
(`correcoes-r7-b/REVISAO-R7-B.md`, veredito APROVADO).
**Fontes do achado:** `03-REVISOR-INTEGRACAO.md` (R7-I-M1/m1/m2) e
`00-CONSOLIDADO-RODADA7.md` §3 e §5, rodada das 01:43 −03 de 09/11/2026.
**Método:** SOMENTE LEITURA — nada editado ou criado fora deste relatório.
Todas as verificações abaixo são de releitura direta dos arquivos e de
contagem mecânica própria; nenhuma afirmação do corretor ou do revisor
foi aceite sem lastro. O SHA de blob `2b669a0d…` da `carga-0011.sql` na
main é citado da mesa (consolidado §3), como declaram corretor e
revisor — não foi re-derivado por mim (leitura da main fora do meu
escopo, como no do revisor 3).

Veredito: CONFIRMADO

## 1. R7-I-M1 (MODERADO) — errata do lote 11 — CONFERE

**Arquivo novo existe:** `base-ficticia-12k/push/ERRATA-LOTE-11.md`
(mtime 2026-11-09 05:19:16 UTC), fora do caminho do executor.

- **(a) A PARADA, com as linhas do `exec.log`:** conferidas verbatim
  por mim em `base-ficticia-12k/push/exec.log` linhas 22–25: lote 11
  (`carga-0011.sql`, 59 KB), tentativa 1 `TIMEOUT 1200s na chamada de
  escrita` (04:05:36), tentativa 2 `Error: an equivalent connector
  write is already running for this request` (04:05:38),
  `PARADA lote 11: nao pousou apos 2 tentativas.` (04:05:38).
  `grep "OK lote 11"`: **zero ocorrências** (contagem própria); OKs
  registados: **32 linhas, lotes 1–10 e 12–33** (contagem própria).
  O mecanismo no código confere: `base-ficticia-12k/push/runner-big.py`
  só acrescenta a `done` com `new_head != prev_head and
  new_subj == chunk["message"]`; na PARADA sai com `sys.exit(1)` sem
  gravar.
- **(b) Verificação na main:** a substância (`carga-0011.sql` na main,
  SHA `2b669a0d…`) é citada da mesa (consolidado §3, R7-I-M1, lido por
  mim — o consolidado declara a substância "confirmada pela mesa" por
  leitura direta, SHA `2b669a0d…`), declarada como tal na errata —
  não apresentada como derivação própria. A corroboração em
  `RELATORIO-COMPLEMENTO-EMASA.md` ("Como cada número foi provado",
  item 3) confere com o citado: blob de cada um dos 45 arquivos na
  main igual ao blob do snapshot pré-complemento. O limite do hash do
  commit do lote 11 (não determinável localmente; log regista lote 10
  → `4ffbff1` e lote 12 → `2e31ae8`) confere com o log lido por mim.
  Nenhum hash inventado.
- **(c) Indeterminação honesta:** a errata declara NÃO DETERMINÁVEL
  quem/o quê alterou o `state.json`, com a janela provada
  04:05:38–04:10:54 UTC. Verifiquei a janela no log: a retomada das
  04:10:54 (`HEAD atual: 8c6dcab`, `AVISO: HEAD difere do estado salvo
  (4ffbff1)`, `LOTE 12/283`) pulou direto para o lote 12, logo `done`
  já continha 11; o `head` salvo seguia `4ffbff1` (HEAD do lote 10),
  logo a edição tocou só `done`. A hipótese da confirmação por leitura
  da main está marcada como hipótese, não como fato. `state.json`
  lido por mim: `"done": [1..33]`, `head 0b97c30…` — confere.
- **(d) Regra de esteira:** presente na errata (`push/state.json`
  nunca é editado fora do runner sem linha de registo no `exec.log`;
  correção manual futura com autor/sessão, instante, motivo,
  antes/depois e verificação na main).
- **Arquivos de estado não tocados:** `push/state.json` e
  `push/exec.log` com mtime **2026-11-09 04:14:28 UTC** — anterior ao
  trabalho do corretor (05:19–05:20 UTC), intactos.

## 2. R7-I-m1 (MENOR) — adendos ao RELATORIO e ao LANDING — CONFERE

Adendos datados de 09/11/2026 presentes ao fim dos dois arquivos
(RELATORIO linhas 116–147; LANDING linhas 383–412), em append puro:
as linhas originais estão intactas — RELATORIO linhas 30–34 e 66–67
ainda trazem "Prestadora Fictícia de Testes — Município Fictício de
Teste", "123.096 linhas", "23.541.654 bytes", "maior arquivo: 99.964
bytes"; LANDING linhas 21 e 31 ainda trazem `carga-0001.sql`
62.708 bytes e `carga-0011.sql` 61.154 bytes. Os adendos supersedem
esses valores sem reescrever nenhuma linha original. O LANDING
regista ainda o estatuto de rascunho original e o plano operativo
(`push/espera/plan.json`).

**Contagem mecânica própria (confirmador), sobre
`entrega/seed/base-ficticia/`:**

| Valor | Adendo | Contagem própria |
|---|---|---|
| Linhas | 123.120 (COPY 121.270 + INSERT 1.850) | COPY data rows **121.270** + INSERT **1.850** = **123.120**; soma de `contagens_por_tabela` do manifesto = **123.120** |
| Bytes de carga | 23.540.541 em 307 arquivos | soma dos 307 `carga-*.sql` = **23.540.541** (soma do manifesto 23.542.600 − `matriz.csv` 2.059 = 23.540.541) |
| Maior arquivo | 99.993 (`carga-0129.sql`) | **99.993**, `carga-0129.sql` |
| `carga-0001.sql` | 66.285 bytes | **66.285** (manifesto: 66.285) |
| `carga-0011.sql` | 61.106 bytes | **61.106** (manifesto: 61.106) |
| Tenant | "Autarquia Ficticia Intermunicipal de Aguas — Porto das Gaivotas e Vale do Rio Claro (TESTE FICTICIO)" | idêntico ao `tenant_nome` do manifesto |

Todos os números conferem por contagem mecânica independente.

## 3. R7-I-m2 (MENOR) — diário C6: runner/teto — CONFERE

**Adendo ao fim de `cascata-ed/RELATORIO-C6.md`** (linhas 154–215) e
**nota `cascata-ed/push/diario-c6/ADENDO-TETO.md`** (arquivo novo,
mtime 05:20:04 UTC) — ambos presentes e coerentes entre si.

- **Determinação "runner-big.py, teto 400.000":** tem lastro nas duas
  linhas independentes alegadas. (1) O adendo pré-existente do próprio
  RELATORIO-C6 (P-C6-DIARIO RESOLVIDA, linhas 138–147, lido por mim)
  já declara a operação "executada por `runner-big.py` em
  `push/diario-c6/`", com commit
  `4d88b8984d3ccec14372249a90f04144441b5865`. (2) Eliminação conferida
  nos artefatos: `cascata-ed/push/diario-c6/exec.log` lido por inteiro
  por mim — PARADA 23:02:27 (`165213 bytes excede o teto de
  ~110 KB`), tentativas 23:02:43 e 23:34:15 com TIMEOUTs e PARADAs
  (23:22:47, 23:54:20), tentativa vitoriosa 03:14:20 com
  `OK lote 1 (tentativa 1) -> HEAD 4d88b89` às 03:14:28; `plan.json`
  segue 1 lote / 1 arquivo / 161 KB (`docs/DIARIO-DE-BORDO.md`);
  `cascata-ed/push/runner.py` tem teto **115.000** (linha 68) e
  `runner-big.py` teto **400.000** (linha 68). 165.213 > 115.000
  exclui o `runner.py` na tentativa vitoriosa. A reconstrução das 4
  invocações no adendo confere linha a linha com o `exec.log`.
  A precisão sobre o §6 confere: o §6 (linhas 121–122) diz "tentada
  duas vezes (23:02 e 23:34)" — às 23:02 houve de facto duas
  invocações (23:02:27 PARADA por teto; 23:02:43 TIMEOUT).
  `state.json` do diário (`done [1]`, head `4d88b898…`) confere com o
  log.
- **Plano nunca dividido:** o `plan.json` segue 1 lote / 1 arquivo —
  a divisão ordenada pela PARADA nunca foi executada e não há divisão
  pendente; o pouso venceu pelo teto maior, agora registado. Correto.
- **Mensagem "~110 KB" em `cascata-ed/push/runner-big.py`:**
  `diff cascata-ed/push/runner.py cascata-ed/push/runner-big.py`
  mostra **só** duas diferenças: a docstring (linha 8,
  "~110 KB" → "~400 KB") e o teto/mensagem da PARADA (linhas 68–69,
  `115_000` → `400_000`, "~110 KB" → "400.000 bytes (~390 KB)"). O
  teto 400.000 já era o valor do `runner-big.py` antes deste lote,
  logo a edição mudou **só o texto das mensagens**, sem mudança de
  comportamento. `python3 -m py_compile` OK (conferido por mim).
  A mensagem do `runner.py` fica como está (teto 115.000 ≈ 110 KB,
  correta). `cascata-ed/push/runner-big.py` mtime 05:20:09 UTC —
  dentro da janela do corretor.
- **Não tocado:** `base-ficticia-12k/push/runner-big.py` mantém a
  mensagem "~110 KB" (linhas 8 e 69), mtime 03:40:04 UTC — o pouso
  corre com ele. Correto não o ter tocado.

## 4. Pouso consolidado — estado verificado, sem perturbação pelo lote

- `base-ficticia-12k/push/espera/exec.log` (lido por mim, fim do
  arquivo): lote 135 OK → HEAD `c8c0b88` às 05:18:48; lote 136
  **PARADA por HTTP 429** às 05:18:54, com o payload a citar o commit
  `983b13f829331f4f42998529e6f95b94cf1340b1` — desfecho do lote 136
  **indeterminado**, como registado na memória da mesa. `espera/state.json`:
  `done` 135 lotes (máx. 135), head `c8c0b88`.
- Os mtimes de `espera/` posteriores ao trabalho do corretor
  (`state.json` 05:18:48, `exec.log` 05:18:54, `plan.json` 04:38:00)
  são do próprio pouso consolidado em curso — nenhuma escrita do
  corretor ali; o conteúdo é só o pouso. O corretor não perturbou o
  pouso em curso.

## 5. P-BF-06/07/08 — CONFERE: aguardam decisão, sem execução

O consolidado §5 ("PERSISTEM"), lido por mim, regista P-BF-06
(tarifa por município NÃO SUPORTA), P-BF-07 (rotas por município
PARCIAL) e P-BF-08 (relatórios/faturamento por município PARCIAL)
como **decisões de desenho do dono** — "vão ao repasse como pendência
de decisão, não como execução". A lista de arquivos tocados pelo lote
(§7 do relatório do corretor) não contém nada sobre P-BF-06/07/08 —
nenhuma execução sobre eles. Confere.

## 6. Geral — afirmações sem lastro e pendências

- **Nenhuma afirmação do corretor sem lastro.** Cada afirmação
  verificável foi conferida acima contra os arquivos; as duas
  indeterminações declaradas (quem editou o `state.json`; hash do
  commit do lote 11) são honestas e estão marcadas como tais.
- **Arquivos tocados pelo lote** (mtimes 05:19–05:20 UTC, todos
  dentro da janela do corretor): `base-ficticia-12k/push/ERRATA-LOTE-11.md`
  (criado), `base-ficticia-12k/RELATORIO-BASE-FICTICIA-12K.md` (adendo),
  `base-ficticia-12k/LANDING.md` (adendo), `cascata-ed/RELATORIO-C6.md`
  (adendo), `cascata-ed/push/diario-c6/ADENDO-TETO.md` (criado),
  `cascata-ed/push/runner-big.py` (só texto). Nenhum outro arquivo do
  pacote foi tocado pelo corretor. **Nenhum push; portão não rodado**
  — conforme declarado.
- **Pendências de acompanhamento registadas pelo corretor** (não são
  bloqueios deste lote): (i) mensagem "~110 KB" em
  `base-ficticia-12k/push/runner-big.py`, a corrigir depois do pouso;
  (ii) linha de abertura runner + teto no `exec.log`, regra prospectiva
  registada, a implementar pela produção. Ambas estão registadas nos
  adendos e no relatório do corretor — nenhuma invalida o lote.

## 7. Observação (não é defeito do lote)

O pouso consolidado da base fictícia **parou por HTTP 429** no lote
136 às 05:18:54 UTC (135/262 lotes, HEAD `c8c0b88`), conforme o
`espera/exec.log` lido nesta confirmação. Com o pouso parado, o motivo
alegado para adiar a correção da mensagem "~110 KB" em
`base-ficticia-12k/push/runner-big.py` (não perturbar o pouso em
curso) deixa de valer no instante atual — a correção pendente pode
ser reavaliada pela sessão principal na retomada, sem que isso
afete o veredito deste lote. Registo apenas como observação para a
mesa; não é ressalva contra o corretor. (Mesma observação já feita
pelo revisor, §6 da revisão — concordo com ela.)

## 8. Distinção das três sessões — CONFERE

- Correção: sessão `b6cf0c91-9a27-46d5-a359-d4a9ddcaecb9`
  (cabeçalho do RELATORIO-CORRECAO-R7-B.md e dos três
  adendos/errata).
- Revisão: sessão `4fa0183b-915a-4029-9eb9-5a9d8d97e134`
  (cabeçalho da REVISAO-R7-B.md).
- Confirmação: sessão `48bd52df-a04e-4225-987a-17d84fa6bc06`
  (cabeçalho deste relatório).
- As três sessões são distintas, com identidades diferentes
  registadas nos cabeçalhos.

## Defeitos encontrados

Nenhum. Nada a corrigir neste lote.

## Conclusão

O lote R7-B está **APTO PARA O POUSO**. O pouso em si é etapa seguinte,
feita por outro agente. Nota: o pouso consolidado da base fictícia
está parado por HTTP 429 (lote 136, 05:18:54 UTC, 135/262 lotes,
HEAD `c8c0b88`), de modo que este lote só pousa em janela sem rate
limit — e, na retomada, a main deve ser conferida antes de qualquer
repulsão (o lote 136 tem desfecho indeterminado, commit `983b13f…`
citado no payload do 429).
