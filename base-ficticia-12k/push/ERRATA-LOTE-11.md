# ERRATA — Lote 11 do pouso antigo da base fictícia (R7-I-M1)

**Data:** 09/11/2026.
**Corretor do lote R7-B (sessão `b6cf0c91-9a27-46d5-a359-d4a9ddcaecb9`).**
**Achado:** R7-I-M1 (moderado, confirmado) — Rodada 7 da auditoria contínua,
relatório do revisor 3 (`03-REVISOR-INTEGRACAO.md`) e consolidado
(`00-CONSOLIDADO-RODADA7.md` §3).
**Natureza:** errata de registro de pouso. Nenhuma linha de `push/state.json`
nem de `push/exec.log` foi tocada por esta errata — ambos foram lidos sem
alteração; a correção vive neste arquivo novo, fora do caminho do executor
(o pouso consolidado da base fictícia corre em rajada por outro executor).

## (a) A PARADA do lote 11 — o que o `exec.log` registra

`base-ficticia-12k/push/exec.log` (horários UTC):

```
03:45:34 LOTE 11/283: feat(seed): base ficticia de testes — carga de UCs ficticias (lote 10/283) (1 arq, 59 KB)
04:05:36 lote 11 tentativa 1: HEAD 4ffbff1->4ffbff1 subj='feat(seed): base ficticia de testes — carga de UCs ficticias (lote 9/2' saida=TIMEOUT 1200s na chamada de escrita
04:05:38 lote 11 tentativa 2: HEAD 4ffbff1->4ffbff1 subj='feat(seed): base ficticia de testes — carga de UCs ficticias (lote 9/2' saida= || Error: an equivalent connector write is already running for this request
04:05:38 PARADA lote 11: nao pousou apos 2 tentativas.
```

Não há nenhuma linha `OK lote 11` no log. Os OKs registados são os dos
lotes 1–10 e 12–33 (32 linhas). O lote 11 é `carga-0011.sql`, 59 KB na
hora do pouso.

## (b) A verificação na main que justificou o `done`

**Substância confirmada pela mesa** (não re-derivada aqui):
`carga-0011.sql` **está na main** — leitura direta da mesa, SHA de blob
`2b669a0d…` (consolidado `00-CONSOLIDADO-RODADA7.md` §3, R7-I-M1).
Corroboração na errata do complemento (`RELATORIO-COMPLEMENTO-EMASA.md`,
errata de 09/11/2026, "Como cada número foi provado", item 3): o SHA de
blob git de cada um dos 45 arquivos na main é igual ao blob do snapshot
pré-complemento — `carga-0011.sql` está entre os 45 (8 de
ferramenta/documentação + `carga-0001.sql`…`carga-0037.sql`) e entre os
19 alterados pelo complemento. A contagem "45 pousados" da errata do
complemento tem lastro para o lote 11. O arquivo está também no plano
consolidado em execução (`push/espera/plan.json`), de modo que o estado
final fica completo independentemente deste registro.

**Limite do que se pode afirmar a partir dos artefatos locais:** o hash
do commit que introduziu `carga-0011.sql` na main **não é determinável
a partir dos artefatos locais**. O log regista o lote 10 → HEAD
`4ffbff1` e o lote 12 → HEAD `2e31ae8`; o commit do lote 11 assenta
entre os dois na main, mas nenhum artefato local regista o seu hash.
Não se inventa um hash aqui.

## (c) Quem/o quê alterou o `state.json` — NÃO DETERMINÁVEL

`push/state.json` traz `"done": [1..33]` — inclui o 11 — com `head`
`0b97c30`. O runner (`base-ficticia-12k/push/runner-big.py`, teto
400.000 bytes) só acrescenta um lote a `done` quando o HEAD muda e o
assunto é a mensagem do lote (`new_head != prev_head and
new_subj == chunk["message"]`); na PARADA sai com `sys.exit(1)` sem
gravar. Não há caminho de código que produza `done` com 11 a partir
desse log.

A retomada às 04:10:54 UTC prova o momento da alteração:

```
04:10:54 HEAD atual: 8c6dcab :: fix(core): ED-69 (B2-10/D2) — SQL pendente alinhado ao Java (fusao R3-D): CHECK
04:10:54 AVISO: HEAD difere do estado salvo (4ffbff1); seguindo do HEAD real.
04:10:54 LOTE 12/283: feat(seed): base ficticia de testes — carga de UCs ficticias (lote 11/283) (1 arq, 97 KB)
```

O processo retomado pulou direto para o lote 12 — logo, `done` já
continha 11 ao carregar. A alteração aconteceu, portanto, entre
**04:05:38 UTC** (PARADA) e **04:10:54 UTC** (retomada). O `AVISO`
mostra que o `head` salvo seguia `4ffbff1` (HEAD do lote 10): a edição
acrescentou 11 à lista `done` **sem alterar `head`**.

**Quem/o quê fez a edição: não determinável a partir dos artefatos.**
Nenhum artefato do pacote nomeia autor, sessão ou ferramenta da
alteração, e nenhum log a regista. Declara-se honestamente a
indeterminação em vez de inventar um autor. (A hipótese de que a
escrita em voo da tentativa 1 tenha pousado no backend e sido
confirmada por leitura da main antes da edição é plausível — o HEAD
não se moveu nas duas tentativas, `4ffbff1->4ffbff1`, e o arquivo está
na main com o SHA citado em (b) — mas a confirmação em si não está
registada em nenhum artefato; fica como hipótese, não como fato.)

## (d) Regra de esteira (prospectiva)

**`push/state.json` nunca é editado fora do runner sem linha de
registo no `exec.log`.** Qualquer correção manual futura ao estado de
um pouso deve ser lavrada no próprio `exec.log` (ou em errata
datada no diretório da operação) com: autor/sessão, instante, motivo,
valores antes/depois e a verificação na main que a justifica
(commit/HEAD e SHA de blob do arquivo). Estado sem registo é estado
não confiável — foi exatamente a ausência dele que gerou este achado.
