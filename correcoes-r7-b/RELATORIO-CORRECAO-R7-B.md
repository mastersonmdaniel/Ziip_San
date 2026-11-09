# Relatório de Correção — Lote R7-B (base fictícia 12k — registos/documentação)

**Corretor do lote R7-B (sessão `b6cf0c91-9a27-46d5-a359-d4a9ddcaecb9`).**
**Data:** 09/11/2026.
**Origem:** achados R7-I-M1 (moderado), R7-I-m1 e R7-I-m2 (menores) da
Rodada 7 da auditoria contínua — relatório do revisor 3
(`03-REVISOR-INTEGRACAO.md`) e consolidado
(`00-CONSOLIDADO-RODADA7.md` §3), rodada das 01:43 −03 de 09/11/2026.
**Escopo fechado:** somente registos/documentação da base fictícia 12k
(+ o diário C6, no achado R7-I-m2). **Nenhum push feito; portão não
rodado** — revisão e confirmação são etapas seguintes.
**Restrição respeitada:** o pouso consolidado da base fictícia corre em
rajada por outro executor; `push/state.json`, `push/exec.log` e
`push/espera/` **não foram tocados** (só lidos; mtimes intactos,
04:14:28 UTC). Todas as correções são por adendo/errata datada, em
append puro — nenhuma linha original reescrita; nada inventado.
Nenhuma escrita foi recusada em cartão.

## 1. R7-I-M1 (MODERADO) — lote 11: `state.json` × `exec.log` — CORRIGIDO POR ERRATA

**Arquivo novo:** `base-ficticia-12k/push/ERRATA-LOTE-11.md` (fora do
caminho do executor; os arquivos de estado existentes não se tocam).

O que a errata regista:

- **(a) A PARADA**, com as linhas do `exec.log` (UTC): lote 11
  (`carga-0011.sql`, 59 KB) — tentativa 1 `TIMEOUT 1200s na chamada de
  escrita` (04:05:36), tentativa 2 `Error: an equivalent connector
  write is already running for this request` (04:05:38), `PARADA lote
  11: nao pousou apos 2 tentativas.` (04:05:38). Nenhuma linha
  `OK lote 11` no log. O mecanismo confirmado no código
  (`base-ficticia-12k/push/runner-big.py`): `done` só cresce com HEAD
  novo + assunto do lote; na PARADA o runner sai com `sys.exit(1)`
  sem gravar.
- **(b) A verificação na main que justificou o `done`:** substância
  confirmada pela mesa e citada como fonte (não re-derivada aqui) —
  `carga-0011.sql` **está na main**, SHA de blob `2b669a0d…`
  (consolidado §3). Corroboração na errata do complemento
  (`RELATORIO-COMPLEMENTO-EMASA.md`, "Como cada número foi provado",
  item 3): o blob de cada um dos 45 arquivos na main é igual ao blob
  do snapshot pré-complemento. O arquivo está também no plano
  consolidado em execução, logo o estado final fica completo
  independentemente deste registo.
- **(c) Quem/o quê alterou o `state.json`: NÃO DETERMINÁVEL a partir
  dos artefatos** — declarado honestamente na errata. O que os
  artefatos provam: a alteração foi entre 04:05:38 UTC (PARADA) e
  04:10:54 UTC (retomada, que pulou direto para o lote 12 — `done` já
  continha 11 ao carregar); o `AVISO` das 04:10:54 mostra o `head`
  salvo ainda em `4ffbff1` (HEAD do lote 10), logo a edição acrescentou
  11 a `done` sem alterar `head`. Nenhum artefato nomeia autor, sessão
  ou ferramenta; nenhum log a regista. A hipótese de confirmação por
  leitura da main antes da edição fica marcada como hipótese, não fato.
- **Limite declarado na errata:** o hash do commit que introduziu
  `carga-0011.sql` na main não é determinável a partir dos artefatos
  locais (o log regista lote 10 → `4ffbff1` e lote 12 → `2e31ae8`; o
  commit do lote 11 assenta entre os dois, sem hash registado
  localmente). Não se inventou um hash.
- **(d) Regra de esteira (prospectiva):** `state.json` nunca é editado
  fora do runner sem linha de registo no `exec.log`; correção manual
  futura exige autor/sessão, instante, motivo, antes/depois e a
  verificação na main (commit/HEAD + SHA de blob).

## 2. R7-I-m1 (MENOR) — RELATORIO e LANDING com números pré-complemento — CORRIGIDO POR ADENDO

Adendo datado de 09/11/2026 em **ambos** os arquivos (append puro):

- `base-ficticia-12k/RELATORIO-BASE-FICTICIA-12K.md` — supersede
  "123.096 linhas", "23.541.654 bytes", "maior arquivo: 99.964 bytes" e
  o nome de tenant antigo.
- `base-ficticia-12k/LANDING.md` — supersede `carga-0001.sql` 62.708
  bytes e `carga-0011.sql` 61.154 bytes; regista o estatuto do LANDING
  (rascunho original; plano operativo é `push/espera/plan.json`).

**Valores corretos pós-complemento (nos dois adendos): 123.120 linhas**
(COPY 121.270 + INSERT 1.850), **23.540.541 bytes** em 307 arquivos,
maior arquivo **99.993 bytes** (`carga-0129.sql`), tenant **"Autarquia
Ficticia Intermunicipal de Aguas — Porto das Gaivotas e Vale do Rio
Claro (TESTE FICTICIO)"**, `carga-0001.sql` **66.285 bytes**,
`carga-0011.sql` **61.106 bytes**. Fontes citadas: revisor 3
(R7-I-m1) + conferência mecânica própria do corretor sobre
`entrega/seed/base-ficticia/` (soma dos 307 `carga-*.sql` =
23.540.541 bytes; COPY 121.270 + INSERT 1.850 = 123.120;
`carga-0001.sql` = 66.285; `carga-0011.sql` = 61.106; maior
`carga-0129.sql` = 99.993). O complemento supersede os números
originais; as linhas originais não foram reescritas.

## 3. R7-I-m2 (MENOR) — diário C6: PARADA por teto — DETERMINADO E REGISTADO

**Adendo datado de 09/11/2026** ao fim de `cascata-ed/RELATORIO-C6.md`
+ nota curta em `cascata-ed/push/diario-c6/ADENDO-TETO.md` (arquivo novo).

**Determinação (não indeterminável): a tentativa vitoriosa das 03:14:20
usou `runner-big.py`, teto 400.000 bytes.** Evidência em duas linhas
independentes: (1) o adendo anterior do próprio RELATORIO-C6.md já
declara a operação "executada por `runner-big.py` em `push/diario-c6/`";
(2) eliminação a partir dos artefatos — o lote tem 165.213 bytes
(`exec.log` 23:02:27; `plan.json`: 1 lote, 1 arquivo, 161 KB);
`runner.py` tem teto 115.000 e pararia antes de tentar a escrita; a
tentativa das 03:14:20 tentou e obteve `OK lote 1 (tentativa 1) ->
HEAD 4d88b89` às 03:14:28 — só o `runner-big.py` (teto 400.000) o
permite. Reconstrução completa das 4 invocações no adendo: 23:02:27
`runner.py` (PARADA por teto); 23:02:43 e 23:34:15 `runner-big.py`
(TIMEOUTs às 23:22:45 e 23:54:18, PARADAs às 23:22:47 e 23:54:20);
03:14:20 `runner-big.py` (OK → `4d88b898`). A troca de runner entre
23:02:27 e 23:02:43 não ficou registada no log. Precisão registada
sobre o §6 do RELATORIO-C6.md: às 23:02 houve duas invocações, não uma.

**Plano nunca dividido — resolvido por esta via:** o `plan.json` segue
1 lote / 1 arquivo / 161 KB; a divisão ordenada pela PARADA nunca foi
executada e não há divisão pendente — o pouso venceu pelo teto maior,
agora registado.

**Mensagem "~110 KB" corrigida:** em `cascata-ed/push/runner-big.py`
(docstring + mensagem de PARADA passam a declarar o teto real de
400.000 bytes, ~390 KB). Alteração só de texto, sem mudança de
comportamento; sintaxe conferida (`py_compile` OK) e diff contra
`runner.py` confirma que só as duas mensagens mudaram. A mensagem do
`runner.py` fica como está (teto 115.000 ≈ 110 KB, correta).

**Regra prospectiva registada (não implementada neste lote):** toda
tentativa de pouso regista runner + teto no `exec.log`.

## 4. O que ficou indeterminável e por quê

| Item | Estado | Porquê |
|---|---|---|
| Quem/o quê editou `base-ficticia-12k/push/state.json` (R7-I-M1, item c) | **Não determinável** | Nenhum artefato do pacote nomeia autor, sessão ou ferramenta; nenhum log regista a edição. Janela provada: 04:05:38–04:10:54 UTC; edição só em `done`, sem tocar `head`. Declarado honestamente na errata; nenhuma hipótese apresentada como fato. |
| Hash do commit que introduziu `carga-0011.sql` na main (R7-I-M1, item b) | **Não determinável a partir dos artefatos locais** | O log local regista lote 10 → `4ffbff1` e lote 12 → `2e31ae8`; o commit do lote 11 não tem hash registado localmente. A substância (arquivo na main, SHA `2b669a0d…`) está provada pela mesa e citada. |

Nada mais ficou indeterminável: o runner/teto do diário C6 foi
determinado por eliminação + declaração prévia (secção 3).

## 5. P-BF-06/07/08 — NÃO executados; aguardam decisão de desenho do dono

Conforme o brief, **nenhuma execução** sobre P-BF-06/07/08. Confirmação
de que seguem aguardando ordem do dono, com as referências exatas:

- **P-BF-06** (tarifa por município NÃO SUPORTA), **P-BF-07** (rotas por
  município PARCIAL) e **P-BF-08** (relatórios/faturamento por município
  PARCIAL) — confirmados nesta rodada com evidência exata pelo revisor 3
  (`03-REVISOR-INTEGRACAO.md` §1(f)) e registados no consolidado
  (`00-CONSOLIDADO-RODADA7.md` §5, "PERSISTEM") como **decisões de
  desenho do dono** — "vão ao repasse como pendência de decisão, não
  como execução" (§6, Repasse). Nenhum deles é achado novo desta
  correção nem recebeu alteração de código, SQL ou documento neste lote.

## 6. Pendências de acompanhamento (não são bloqueios deste lote)

- **Mensagem "~110 KB" em `base-ficticia-12k/push/runner-big.py`:**
  o mesmo texto enganoso existe lá (teto 400.000, mensagem "~110 KB"),
  mas o arquivo **não foi tocado** porque o pouso consolidado corre com
  ele neste momento. Corrigir depois do pouso, pelo mesmo padrão
  aplicado em `cascata-ed/push/runner-big.py`.
- **Linha de abertura runner + teto no `exec.log`:** regra prospectiva
  registada nos adendos do R7-I-m2; a implementar nos runners pela
  produção (não implementada aqui).

## 7. Arquivos tocados por este lote (verificação)

| Arquivo | Ação |
|---|---|
| `base-ficticia-12k/push/ERRATA-LOTE-11.md` | Criado (errata R7-I-M1) |
| `base-ficticia-12k/RELATORIO-BASE-FICTICIA-12K.md` | Adendo datado em append (R7-I-m1) |
| `base-ficticia-12k/LANDING.md` | Adendo datado em append (R7-I-m1) |
| `cascata-ed/RELATORIO-C6.md` | Adendo datado em append (R7-I-m2) |
| `cascata-ed/push/diario-c6/ADENDO-TETO.md` | Criado (nota curta R7-I-m2) |
| `cascata-ed/push/runner-big.py` | Correção de texto (2 mensagens "~110 KB" → teto 400.000); só texto, sintaxe OK |
| `correcoes-r7-b/RELATORIO-CORRECAO-R7-B.md` | Este relatório |

**Não tocados:** `base-ficticia-12k/push/state.json`,
`base-ficticia-12k/push/exec.log`, `base-ficticia-12k/push/espera/`,
`base-ficticia-12k/push/runner-big.py`, nenhuma linha original de
nenhum relatório. **Nenhum push; portão não rodado.** Segue para
revisão e confirmação independentes.

---

## Adendo de desfecho — executor do pouso, 09/11/2026 (sessão `db501767-5263-441e-b577-bb78f6ccc024`)

Adendo do executor do pouso, em append puro — nenhuma linha revisada ou
confirmada foi tocada. Este adendo pousa junto com o lote (é parte do
RELATORIO-CORRECAO-R7-B.md na lista do push).

- **Portão mecânico (1ª rodada, 05:30 UTC): exit 1** — Regra 3 (o
  extrator não reconhece o rótulo "Corretor" no cabeçalho do RELATORIO)
  e Regra 5 (`LANDING.md` ausente). Regra 3 demonstrada como falso
  positivo em `correcoes-r7-b/PORTAO-REGISTRO-R7-B.md` (§3.2 do
  PROTOCOLO-ESTEIRA.md; o portão NÃO foi editado). Regra 5 resolvida
  com a produção do `LANDING.md` do lote (artefato local, não pousa).
  **2ª rodada (05:38 UTC): exit 1 só na Regra 3** (Regras 1, 2, 4, 5 e
  6 OK) — o único item restante é o falso positivo demonstrado no
  PORTAO-REGISTRO-R7-B.md.
- **Pouso consolidado da base fictícia:** parado por HTTP 429 no lote
  136 às 05:18:54 UTC (HEAD `c8c0b88`, 135/262). Verificação do
  executor na main: o commit `983b13f829331f4f42998529e6f95b94cf1340b1`
  (mensagem "lote 136/262", 05:18:54Z) **EXISTE** — o 429 veio DEPOIS
  da criação do commit. **Desfecho do lote 136: RESOLVIDO (pousou).**
  O `state.json` local da base (135/262, head `c8c0b88`) está
  desatualizado — a reconciliação é do executor do pouso consolidado
  na retomada; **não repulsar o lote 136**.
- **Decisão do executor: NÃO POUSAR nesta janela.** O último desfecho
  de escrita conhecido é o HTTP 429 (05:18:54 UTC, ~20 min atrás) e
  não há evidência de fim do rate limit; nova tentativa seria retry
  contra limite ativo, proibido pelo brief. Lote preparado: portão
  (acima) + rebase verificado contra o HEAD `983b13f` + plano e tabela
  de hashes em `PORTAO-REGISTRO-R7-B.md`.
- **Estado: CONFIRMADO, GATE-APROVADO, AGUARDANDO JANELA SEM RATE
  LIMIT.** O pouso (lote único + verbete de diário mesclado sobre o
  blob vivo + verificação hash a hash) será executado na janela
  seguinte, com o rebase re-verificado contra o HEAD corrente no ato
  (a main anda — o pouso consolidado ainda tem 126 lotes + diário).
